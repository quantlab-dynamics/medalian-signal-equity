package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.redisService.StrategyPnlCacheService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.strategy.SignalService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.common.utils.staticstore.dropdownutils.TgtMenu.PERCENT_OF_ENTRY_PRICE;

@Service
public class DiyStrategyService {

    private static final Logger logger = LogManager.getLogger(DiyStrategyService.class);

    // Replaced it with redis strategyPnlCacheService service
    public static ConcurrentHashMap<Long, Double> liveStrategyPNL = new ConcurrentHashMap<>();

    private final SignalRepository signalRepository;

    private final TouchLineService touchLineService;

    private final SignalService signalService;

    private final CommonUtils commonUtils;

    private final GrpcService grpcService;

    private final MarketDataFetch marketDataFetch;

    private final StrategyRepository strategyRepository;

    private final StrategyLegRepository strategyLegRepository;

    private final GrpcErrorService grpcErrorService;

    @Autowired
    private StrategyPnlCacheService strategyPnlCacheService;

    @Autowired
    public DiyStrategyService(SignalRepository signalRepository,TouchLineService touchLineService,SignalService signalService,CommonUtils commonUtils , GrpcService grpcService , MarketDataFetch marketDataFetch , StrategyLegRepository strategyLegRepository, StrategyRepository strategyRepository , GrpcErrorService grpcErrorService) {
        this.signalRepository = signalRepository;
        this.touchLineService = touchLineService;
        this.signalService = signalService;
        this.commonUtils = commonUtils;
        this.grpcService = grpcService;
        this.marketDataFetch = marketDataFetch;
        this.strategyLegRepository = strategyLegRepository;
        this.strategyRepository = strategyRepository;
        this.grpcErrorService = grpcErrorService;
    }

    public boolean checkDiyEntry(Strategy strategy) {
        // has to check the pause the strategy
        if (StrategyOption.ENABLE_HOLD.getKey().equalsIgnoreCase(strategy.getHoldType())){
            return false ;
        }
        // has to check for the entry conditions
        if (strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
            Hibernate.initialize(strategy.getEntryDetails());
            EntryDetails entryDetails = strategy.getEntryDetails();

            ZoneId zoneId = ZoneId.systemDefault();
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(zoneId);
            // Convert both Instants to ZonedDateTime
            ZonedDateTime nowDateTime = now.atZone(zoneId);
            // Extract hours and minutes
            int entryHour = entryDetails.getEntryHourTime();
            int entryMinute = entryDetails.getEntryMinsTime();
            int nowHour = nowDateTime.getHour();
            int nowMinute = nowDateTime.getMinute();
            ExitDetails exitDetails = strategy.getExitDetails();
            //Instant entryTime = entryDetails.getEntryTime();
            Instant entryTime = LocalDateTime.of(today, LocalTime.of(entryDetails.getEntryHourTime(),entryDetails.getEntryMinsTime() )).atZone(zoneId).toInstant();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(),exitDetails.getExitMinsTime() )).atZone(zoneId).toInstant();
            // Convert Instant to LocalDateTime without applying any zone shift
            if ((now.isAfter(entryTime)  && now.isBefore(exit)) || (entryDetails.getEntryHourTime() == nowHour && entryDetails.getEntryMinsTime() == nowMinute)) {
                logger.info("Exit Time In UAT: "+exit+"Entry Time In UAT: "+entryTime+" Now Time In UAT: "+now+" strategyID = "+strategy.getId()+" ,"+"entryHour: " + entryHour + ", entryMinute: " + entryMinute + " :: nowHour: " + nowHour + ", nowMinute: " + nowMinute);
                logger.info("Exit Time In UAT: "+exit+" Now Time In UAT: "+now+" strategyID = "+strategy.getId()+" ,"+"entryHour: " + entryHour + ", entryMinute: " + entryMinute + " :: nowHour: " + nowHour + ", nowMinute: " + nowMinute);
                logger.info("Strategy is triggered ");
                // now has to check the Multiple Signals are there or not
                List<Signal> signals = signalRepository.findSignalsByPositionTypeAndStrategyLive(StrategyType.POSITIONAL.getKey(), StrategyType.INTRADAY.getKey(),strategy.getId());
                return true;
            } else {
                return false;
            }
        }
        return false;
    }


    @Transactional
    public boolean checkDiyExit(Strategy strategy) {

        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }
            Hibernate.initialize(strategy.getExitDetails());
            ExitDetails exitDetails = strategy.getExitDetails();

            if (exitDetails == null){
                return false;
            }

            if (checkExitTimeReached(strategy, exitDetails)) {
                return true;
            }
            if (checkStrategyStopLoss(strategy, exitDetails))
                return true;
        }
        diyLegStopLossCheckAndExit(strategy);
        return false;
    }

    private Boolean checkStrategyStopLoss(Strategy strategy, ExitDetails exitDetails) {
        try {
            Double strategyProfitLoss = strategyPnlCacheService.getStrategyPnl(strategy.getId());

            Double profitMtmUnitValue = null;
            Double stoplossMtmValue = null;

            if (exitDetails.getTargetUnitToggle().equalsIgnoreCase(TOGGLE_TRUE)
                    && exitDetails.getTargetUnitType() != null) {
                double res = exitDetails.getProfitMtmUnitValue();
                profitMtmUnitValue = (double) Math.round(res);
            }

            if (exitDetails.getStopLossUnitToggle().equalsIgnoreCase(TOGGLE_TRUE)
                    && exitDetails.getStopLossUnitType() != null) {
                double res = exitDetails.getStoplossMtmUnitValue();
                stoplossMtmValue = (double) Math.round(res);
            }

            return profitLossCheck(strategyProfitLoss, profitMtmUnitValue, stoplossMtmValue);

        } catch (Exception e) {
            logger.error("Error while calculating strategy P&L for strategy id: {}, exception = {}",
                    strategy.getId(), e);
        }
        return false;
    }

    private Boolean profitLossCheck(Double strategyProfitLoss, Double profitMtmUnitValue, Double stopLossMtmValue) {
        if (strategyProfitLoss == null) {
            return false;
        }
        if (profitMtmUnitValue != null && strategyProfitLoss >= profitMtmUnitValue) {
            return true;
        }
        if (stopLossMtmValue != null && strategyProfitLoss <= -stopLossMtmValue) {
            return true;
        }
        return false;
    }

    private void diyLegStopLossCheckAndExit(Strategy strategy) {

        if (strategy.getStrategyCategory().getId() !=4){
            Optional<Signal> OptionalSignal = signalRepository.findFirstByStrategyIdAndStatusOrderByCreatedAtDesc(strategy.getId(), SignalStatus.LIVE.getKey());
            if (OptionalSignal.isEmpty()) {
                return;
            }
            Signal signal = OptionalSignal.get();

            // has to check individual profit and loss of the strategy and exit accordingly
            Hibernate.initialize(signal.getSignalLegs());
            List<StrategyLeg> legs = new ArrayList<>(signal.getSignalLegs());
            boolean isExit = false;

            for (StrategyLeg leg : legs) {
                if (leg.getStatus().equalsIgnoreCase(Status.LIVE.getKey()) && (leg.getStopLossUnitType() != null || leg.getTargetUnitType() != null)) {
                    if (TOGGLE_TRUE.equalsIgnoreCase(leg.getTrailingStopLossToggle())) {
                        isExit = changeTrailingStopLoss(leg);
                    } else {
                        isExit = checkLegStopProfitLoss(leg);
                    }
                }
            }
            if (isExit) {
                try {
                    triggerExitOrder(strategy, legs, signal);
                }catch (Exception e) {
                    logger.error("Error while triggering exit order for strategy leg: " + strategy.getId(), e);
                    //  grpcErrorService.sendErrorToGrpc(e, "Error while triggering exit order for strategy leg: " + strategy.getId());
                }

            }
//            else {
//                strategyLegRepository.saveAll(legs);
//            }
        }
    }

    private boolean checkExitTimeReached(Strategy strategy, ExitDetails exitDetails) {

        Instant now = Instant.now();
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime nowDateTime = now.atZone(zoneId);
        int nowHour = nowDateTime.getHour();
        int nowMinute = nowDateTime.getMinute();

        // Extract hours and minutes from strategy
        int exitHour = exitDetails.getExitHourTime();
        int exitMinute = exitDetails.getExitMinsTime();


        if (strategy.getExecutionType().equalsIgnoreCase(StrategyType.POSITIONAL.getKey())){
            LocalDate expiryDate= LocalDate.parse(commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()));
            LocalDate nowDate = LocalDate.now();

            return expiryDate.equals(nowDate) && (exitHour == nowHour && exitMinute == nowMinute);
        }else {
            return exitHour == nowHour && exitMinute == nowMinute;
        }
    }

    public void checkTrailingStopLoss(Strategy strategy) {
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            Hibernate.initialize(strategy.getStrategyLeg());
            List<StrategyLeg> legs = strategy.getStrategyLeg();
            for (StrategyLeg leg : legs) {
                if (leg.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                    // has to calculate the leg level profit and loss

                }
            }
        }
    }

    public boolean checkLegStopProfitLoss(StrategyLeg strategyLeg) {

        if (strategyLeg == null || strategyLeg.getExecutedPrice() == null) {
            logger.debug("Warning: StrategyLeg or executed price is null. ");
            return false;
        }

        MarketData marketData = touchLineService.getTouchLine(String.valueOf(strategyLeg.getExchangeInstrumentId()));

        if (marketData == null) {
            logger.debug("Warning: Market data or LTP is not available for stopLoss calculation.");
            return false;
        }
        double executedPrice = strategyLeg.getExecutedPrice()/(double) AMOUNT_MULTIPLIER;

        double sign = strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.SELL.getKey())? -1: 1;
        double profitLoss = (long) (marketData.getLTP() - executedPrice) * strategyLeg.getQuantity() * sign;
        double stopLossValue = requiredValue(strategyLeg.getTrailingStopLossToggle(),strategyLeg.getStopLossUnitType(), strategyLeg.getStopLossUnitValue(), executedPrice);
        double targetValue = requiredValue(strategyLeg.getTargetUnitToggle(), strategyLeg.getTargetUnitType(), strategyLeg.getTargetUnitValue(), executedPrice);

        if (profitLoss >= 0 && targetValue >= 0
                && TOGGLE_TRUE.equalsIgnoreCase(strategyLeg.getTargetUnitToggle()) && profitLoss >= targetValue) {
            strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
            return true;
        }

        if (profitLoss <= 0 && stopLossValue >= 0
                && TOGGLE_TRUE.equalsIgnoreCase(strategyLeg.getStopLossUnitToggle()) && (- profitLoss) >= stopLossValue) {
            strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
            return true;
        }
        // If no exit condition is met, return false
        return false;
    }


    public boolean changeTrailingStopLoss(StrategyLeg strategyLeg) {
        if (strategyLeg.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            // Fetch the latest touchline data
            MarketData touchlineBinaryResponse = touchLineService.getTouchLine(String.valueOf(strategyLeg.getExchangeInstrumentId()));
            if (touchlineBinaryResponse != null) {
                // Current market price
                double currentPrice = touchlineBinaryResponse.getLTP();
                // Trailing distance
                double trailingDistance = strategyLeg.getTrailingDistance();

                // for Long Positions
                if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.BUY.getKey())) {
                    double stopLossValueDistance = currentPrice - trailingDistance;

                    // trailing stop loss if conditions are met
                    if (currentPrice > (strategyLeg.getExecutedPrice()/(double) AMOUNT_MULTIPLIER) && stopLossValueDistance > strategyLeg.getTrailingStopLossPoints()) {
                        long newStopLoss = (long) stopLossValueDistance;
                        strategyLeg.setTrailingStopLossPoints(newStopLoss);
                        return false;
                    }

                    // Trigger exit order if ltp is less than or equal to stop loss
                    if (currentPrice <= strategyLeg.getTrailingStopLossPoints()) {
                        strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
                        // triggerExitOrder(strategyLeg);
                        return true;
                    }
                }
                // for Short Positions
                else if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.SELL.getKey())) {
                    double stopLossValueDistance = currentPrice + trailingDistance;

                    // trailing stop loss if conditions are met
                    if (currentPrice < (strategyLeg.getExecutedPrice()/(double) AMOUNT_MULTIPLIER) && stopLossValueDistance < strategyLeg.getTrailingStopLossPoints()) {
                        long newStopLoss = (long) stopLossValueDistance;
                        strategyLeg.setTrailingStopLossPoints(newStopLoss);

                        return false;
                    }
                    // Triggering the  exit order if ltp is greater than or equal to Stop loss
                    if (currentPrice >= strategyLeg.getTrailingStopLossPoints()) {
                        strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
                        // triggerExitOrder(strategyLeg);
                        return true;
                    }
                }
            }
        }
        return false;
    }



    private void triggerExitOrder(Strategy strategy, List<StrategyLeg> strategyLegs, Signal signal) {

        logger.info("Triggering exit order for strategy leg: " + strategy.getId());
        Signal newSignal = signalService.createSingleExitWithList(strategy, strategyLegs,signal);
        if (newSignal == null) {
            logger.error("Failed to create exit signal for strategy leg: " + strategy.getId());
            return;
        }
        if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
            // Assuming grpcService is available to send the exit signal
            grpcService.sendExitSignal(newSignal);
            logger.info("Exit signal sent for strategy leg: " + strategy.getId());
        } else {
            logger.info("Exit signal created for strategy leg: " + strategy.getId() + " but not sent due to paper trading mode.");
        }

    }

    private Double requiredValue(String toggle, String type, Long selectedValue, Double executedPrice) {
        if (TOGGLE_TRUE.equalsIgnoreCase(toggle)) {
            if (TgtMenu.PERCENT_OF_ENTRY_PRICE.getKey().equalsIgnoreCase(type)) {
                return executedPrice * selectedValue / 100;
            } else {
                return Double.valueOf(selectedValue);
            }
        }
        return 0.0;
    }
}
