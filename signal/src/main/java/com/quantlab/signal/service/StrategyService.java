package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.DeltaNeutralExit;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.*;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.strategy.SignalService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.apache.logging.log4j.LogManager;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service
@Transactional
public class StrategyService {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(StrategyService.class);

    private static final Logger log = LoggerFactory.getLogger(StrategyService.class);


    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    GrpcService grpcService;

    @Autowired
    UnderlyingRespository underlyingRespository;

    @Autowired
    EntryDetailsRepository entryDetailsRepository;

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    SignalAdditionsRepository signalAdditionsRepository;

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    SignalService signalService;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    GrpcErrorService grpcErrorService;

    Signal collarExitSignal;

    public boolean deltaNeutralExitCheck(String underling,Signal signal, Strategy strategy){
        try {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }
            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);

            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(),exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();

            Instant nowTime = Instant.now();
            if (exit.equals(nowTime) || nowTime.isAfter(exit) ){
                return true;
            }
            List<DeltaNeutralCheckDto> check =  Arrays.stream(DeltaNeutralExit.values()).map((dto)-> new DeltaNeutralCheckDto(dto.getKey(),dto.getPositive(),dto.getNegative())).toList();
            double profitAndLoss = 0;
            int callLots = 0;
            int putLots = 0;
            List<StrategyLeg> legs = signal.getSignalLegs();
            for(StrategyLeg leg : legs){
                if (leg.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())) {
                    if (leg.getOptionType().equalsIgnoreCase(SegmentType.CE.getKey())) {
                        callLots = Math.toIntExact(leg.getNoOfLots());
                    } else if (leg.getOptionType().equalsIgnoreCase(SegmentType.PE.getKey())) {
                        putLots = Math.toIntExact(leg.getNoOfLots());
                    }

                }
            }
            DeltaNeutralCheckDto checkDto = check.stream().filter(dto -> dto.getLabel().equalsIgnoreCase(underling)).findFirst().orElse(null);

            Hibernate.initialize(signal.getSignalAdditions());
            SignalAdditions signalAdditions = signal.getSignalAdditions();
            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());

            double entryPrice = (double) signalAdditions.getEntryUnderlingPrice() /AMOUNT_MULTIPLIER;

            int different = Math.abs((int)entryPrice-(int) syntheticPrice);

            if (callLots>putLots){
                if (entryPrice > syntheticPrice) {
                    if (different > checkDto.getPositive()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice()/AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg" + different + " > " + checkDto.getPositive() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    }else return false;
                }else if (entryPrice < syntheticPrice) {
                    if(different > checkDto.getNegative()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice()/AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg" + different + " > " + checkDto.getNegative() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    }else return false;
                }

            }else if (callLots<putLots){
                if (entryPrice > syntheticPrice) {
                    if (different > checkDto.getNegative()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice()/AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg"+ different + " > " + checkDto.getNegative() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    }else return false;
                }else if (entryPrice < syntheticPrice) {
                    if (different > checkDto.getPositive()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice()/AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg" + different + " > " + checkDto.getPositive() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    }else return false;
                }
            }
            else {
                if (different > checkDto.getNegative()){
                    System.out.println("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice()/AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                    logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg index is " +checkDto.getLabel() +"UNDERLING NAME : "+underling+"  ------ "+ "difference "  + different + " > " + checkDto.getNegative() + ", callLots: " + callLots + ", putLots: " + putLots);
                    return  true;
                }else return false;
            }
            return false;
        }catch (Exception e){
            logger.error("Error in delta neutral exit check: "+underling+" , "+signal.getId()+" , ");
            logger.error("Error in delta neutral exit check: "+e.getMessage());
            return false;
        }
    }

    public EntryExitTimes entryExitTimes  (Strategy strategy){

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
        Instant entryTime = LocalDateTime.of(today, LocalTime.of(entryHour, entryMinute)).atZone(zoneId).toInstant();
        Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(),exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();
        EntryExitTimes exitTimes = new EntryExitTimes();
        exitTimes.setNowInstantTime(now);
        exitTimes.setEntryMinute(entryMinute);
        exitTimes.setEntryHour(entryHour);
        exitTimes.setNowHour(nowHour);
        exitTimes.setNowMinute(nowMinute);
        exitTimes.setExitInstantTime(exit);
        exitTimes.setEntryInstantTime(entryTime);

        return exitTimes;
    }

    public boolean collarEntry(Strategy strategy) {

        if (strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
            ZoneId zoneId = ZoneId.systemDefault();
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(zoneId);

            Instant entryTime = LocalDateTime.of(today, LocalTime.of(9, 20))
                    .atZone(zoneId)
                    .toInstant();

            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exitTime = LocalDateTime.of(today,
                            LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime()))
                    .atZone(zoneId)
                    .toInstant();

            if (now.isAfter(entryTime) && now.isBefore(exitTime)) {
                logger.info("Strategy is triggered");
                return true;
            }
        }
        return false;
    }

    @Transactional
    public boolean collarExit(Strategy strategy) {
        try {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }
            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);
            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();

            Instant nowTime = Instant.now();
            if (exit.equals(nowTime) || nowTime.isAfter(exit)) {
                return true;
            }
            List<Signal> activeSignal = getActiveSignals(strategy.getId(), SignalStatus.LIVE.getKey());
            if (activeSignal.isEmpty()) {
                return false;
            } else {
                activeSignal.sort(Comparator.comparingInt(dto -> Math.toIntExact(dto.getId())));
                Optional<Signal> signalOptional = signalRepository.findByIdForUpdate(activeSignal.get(0).getId());
                if (signalOptional.isEmpty()) {
                    return false;
                }
                Signal signal = signalOptional.get();
                collarExitSignal = signal;
                SignalAdditions signalAdditions = signal.getSignalAdditions();

                int previousATM = signalAdditions.getCurrentAtm();
                double previousIndexPrice = signal.getBaseIndexPrice()/ (double) AMOUNT_MULTIPLIER;
                double price;

                if (strategy.getAtmType().equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey()) || strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()))
                    price = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());
                else
                    price = marketDataFetch.getMarketData(strategy).getSpotPrice();
                String expiryDate = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey());
                double priceDifference = Math.abs(price - previousIndexPrice);
                while (priceDifference > STRIKE_INTERVAL) {
                    List<StrategyLeg> strategyLegs = signal.getStrategyLeg();
                    int strikeDiff = price > previousATM ? -200 : 200;
                    int offset = price > previousATM ? 1 : -1;
                    exitStrike(strategyLegs, strategy, previousATM, expiryDate, strikeDiff);

                    int entryATM = previousATM + (300 * offset) ;
                    SignalMapperDto signalMapperDtoCE = createSingleSignalMapperDto(strategy,"CE", offset, entryATM);
                    SignalMapperDto signalMapperDtoPE = createSingleSignalMapperDto(strategy, "PE", offset, entryATM);
                    List<StrategyLeg> createdLegs = signalService.createStrategyLegs(List.of(signalMapperDtoCE, signalMapperDtoPE),
                            strategy, signal, SegmentType.PE.getKey());
                    previousATM = previousATM + (offset * STRIKE_INTERVAL);
                    signalAdditions.setCurrentAtm(previousATM);
                    signal.setBaseIndexPrice((long) (price * AMOUNT_MULTIPLIER));

                    List<StrategyLeg> newLegs = strategyLegRepository.saveAll(createdLegs);
                    newLegs = assignLegIdentifiers(newLegs, signal);
                    signalAdditionsRepository.saveAndFlush(signalAdditions);
                    priceDifference = priceDifference - STRIKE_INTERVAL;
                    signal.getSignalLegs().addAll(newLegs);
                }
                if(Math.abs(price - previousIndexPrice) > STRIKE_INTERVAL &&
                        signal.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey()) &&
                        signal.getStrategy().getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())){
                    logger.info("Collar exit with dynamic adjustment for strategy ID: " + strategy.getId() + ", Signal ID: " + signal.getId());
                    sendGRPCModificationOrders(signal.getId(), strategy);
                }
                return false;
            }
        } catch (Exception e) {

            grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, collarExitSignal, Status.ERROR.getKey());
            logger.error("Error in collar exit with dynamic adjustment: " + e.getMessage());
            return false;
        }
    }

    @Transactional
    public List<StrategyLeg> assignLegIdentifiers(List<StrategyLeg> newLegs, Signal signal) {
        for (StrategyLeg leg : newLegs) {
            leg.setLegIdentifier("QO_" + signal.getId() + "_" + leg.getId());
        }
        return strategyLegRepository.saveAllAndFlush(newLegs);
    }

    @Transactional
    public void sendGRPCModificationOrders(Long signalId, Strategy strategy) {

        Optional<Signal> signalOptional = signalRepository.findById(signalId);
        if (signalOptional.isEmpty()) {
            logger.error("Signal not found for ID: " + signalId);
            grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, null, Status.ERROR.getKey());
            return;
        }

        Signal signal = signalOptional.get();
        sendCollarExit(signal, strategy);
        sendCollarEntry(signal, strategy);
    }

    private void sendCollarEntry(Signal signal, Strategy strategy) {
        try {
            grpcService.sendSignal(signal);
            logger.info("Collar Entry signal sent successfully for strategy ID: " + strategy.getId() + ", Signal ID: " + signal.getId());
        } catch (Exception e) {
            logger.error("Error in sending collar Entry signal: " + e.getMessage());
            grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, signal, Status.ERROR.getKey());
        }
    }

    private void sendCollarExit(Signal signal, Strategy strategy) {
        try {
            grpcService.sendExitSignal(signal);
            logger.info("Collar exit signal sent successfully for strategy ID: {}, Signal ID: {}", strategy.getId(), signal.getId());
        } catch (Exception e) {
            logger.error("Error in sending collar exit signal: " + e.getMessage());
            grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, signal, Status.ERROR.getKey());
        }
    }

    public boolean inHouseEntryCheck(Strategy strategy){

        EntryExitTimes entryExitTimes = entryExitTimes(strategy);
        if ((entryExitTimes.getNowInstantTime().isAfter(entryExitTimes.getEntryInstantTime())  && entryExitTimes.getNowInstantTime().isBefore(entryExitTimes.getExitInstantTime()) )||(entryExitTimes.getEntryHour() == entryExitTimes.getNowHour() && entryExitTimes.getEntryMinute() == entryExitTimes.getNowMinute())) {
            Optional<Strategy> strategy1 = strategyRepository.findById(strategy.getId());
            if ((entryExitTimes.getExitInstantTime().equals(entryExitTimes.getNowInstantTime()) || (entryExitTimes.getExitInstantTime().isAfter(entryExitTimes.getNowInstantTime()))) && strategy1.isPresent() && (strategy1.get().getStatus().equalsIgnoreCase(Status.ACTIVE.getKey()) || strategy1.get().getStatus().equalsIgnoreCase(StrategyStatus.EXIT.getKey()))) {
//                logger.info("strategyID = "+strategy.getId()+" ,"+"entryHour: " + entryExitTimes.getEntryHour() + ", entryMinute: " + entryExitTimes.getEntryMinute() + " :: nowHour: " + entryExitTimes.getNowHour() + ", nowMinute: " + entryExitTimes.getNowMinute());
//                logger.info("resignal count: " + strategy1.get().getReSignalCount() + ", signal count: " + strategy1.get().getSignalCount() + ", manual exit type: " + strategy1.get().getManualExitType());
                return (strategy1.get().getReSignalCount() > strategy1.get().getSignalCount()) && strategy1.get().getManualExitType().equalsIgnoreCase(ManualExit.DISABLED.getKey());
            }
        }
        return false;
    }

    private SignalMapperDto createSingleSignalMapperDto(Strategy strategy, String optionType, int offSet1, int strikeATM) {
        MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
        // 2. Compute synthetic price
        String expiryDate = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey());

        String key = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + expiryDate + "-" + strikeATM + optionType;
        MasterResponseFO master = marketDataFetch.getMasterResponse(key);
        MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
        SignalMapperDto leg = new SignalMapperDto();
        leg.setMarketLiveDto(marketLive);
        leg.setTouchlineBinaryResposne(data);
        leg.setLegName(key);
        leg.setMasterData(master);
        leg.setBuySellFlag(LegSide.SELL.getKey());
        leg.setSegment("NSEFO");
        leg.setCategory(optionType.equals("CE") ? LegType.CALL.getKey() : LegType.PUT.getKey());
        leg.setPositionType(strategy.getPositionType());
        leg.setLegType(LegType.OPEN.getKey());
        leg.setDerivativeType(OptionType.OPTION.getKey());
        String lotsKey = optionType.equals("CE") ? "call" : "put";
        leg.setLots(1L);
        leg.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

        return leg;
    }

    @Transactional
    public List<Signal> getActiveSignals(Long strategyId, String status) {
        return signalRepository.findByStrategyIdAndStatus(strategyId, status);
    }

    void exitStrike(List<StrategyLeg> strategyLegs, Strategy strategy, int previousATM, String expiryDate, int strikeJump){
        String keyPE = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + expiryDate + "-" + (previousATM + strikeJump) + "PE";
        String keyCE = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + expiryDate + "-" + (previousATM + strikeJump) + "CE";

        StrategyLeg ceLeg = strategyLegs.stream()
                .filter(leg -> keyCE.equalsIgnoreCase(leg.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("CE leg not found"));

        StrategyLeg peLeg = strategyLegs.stream()
                .filter(leg -> keyPE.equalsIgnoreCase(leg.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("PE leg not found"));

        signalService.createSingleExit(strategy, peLeg);
        signalService.createSingleExit(strategy, ceLeg);
    }
}
