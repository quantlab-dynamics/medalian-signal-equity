package com.quantlab.signal.strategy;

import com.quantlab.common.entity.*;
import com.quantlab.common.loggingService.DeploymentErrorService;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.IndexDifference;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import com.quantlab.signal.dto.LegOrderDto;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.GrpcErrorService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.utils.DiyStrategyCommonUtil;
import com.quantlab.signal.utils.StrategyUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.StrategyConstants.DEFAULT_AMOUNT_INTERVAL;

@Service
public class SignalService {

    private static final Logger logger = LoggerFactory.getLogger(SignalService.class);

    private final SignalRepository signalRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    EntityManager entityManager;

    @Autowired
    GrpcErrorService grpcErrorService;


    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    DiyStrategyCommonUtil diyStrategyCommonUtil;

    private final MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    GrpcService grpcService;

    @Autowired
    DefaultTransactionDefinition defaultTransactionDefinition;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DeploymentErrorService deploymentErrorService;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    StrategyUtils strategyUtils;

    @Autowired
    GrpcErrorService GrpcErrorService;


    public SignalService(SignalRepository signalRepository , MarketDataFetch marketDataFetch ,
                         StrategyRepository strategyRepository,
                         StrategyLegRepository strategyLegRepository,
                         TouchLineService touchLineService , GrpcErrorService grpcErrorService)
    {
        this.signalRepository = signalRepository;
        this.marketDataFetch = marketDataFetch;
        this.strategyRepository = strategyRepository;
        this.strategyLegRepository = strategyLegRepository;
        this.touchLineService = touchLineService;
        this.grpcErrorService = grpcErrorService;


    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Signal createSignal(Strategy strategy, List<SignalMapperDto> signalMapperDto)  {

        logger.info("Creating new signal for strategy: " + strategy.getId());
        // Create a new Signal object and set its fields
        Strategy strategy1 = strategyRepository.findById(strategy.getId()).orElse(null);
        if (strategy1 == null) {
            // has to handle
            return null;
        }
        Optional<Signal> signal1 = signalRepository.findFirstByStrategyIdAndStatusOrderByCreatedAtDesc(strategy.getId(), SignalStatus.LIVE.getKey());
        if (signal1.isPresent()){
            strategy1.setStatus(Status.ERROR.getKey());
            signal1.get().setStatus(Status.ERROR.getKey());
            deploymentErrorService.saveStrategyUpdateLogs(strategy1, "Strategy tried to create a new signal even when there is live signal existing  : "+strategy1.getId());
            strategyRepository.saveAndFlush(strategy1);
            signalRepository.saveAndFlush(signal1.get());
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.now();
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

        Signal signal = new Signal();
        signal.setAppUser(strategy1.getAppUser());
        signal.setStrategy(strategy1);
        signal.setMultiplier(strategy1.getMultiplier());
        signal.setCapital(strategy1.getMinCapital());
        signal.setExecutionType(strategy1.getExecutionType());
        signal.setDeployedOn(instant.toString());
        signal.setPositionType(strategy.getPositionType());
        signal.setCapital(strategy.getMinCapital());
        signal.setStatus(SignalStatus.LIVE.getKey());
        List<StrategyLeg> newLegs = createStrategyLegs(signalMapperDto, strategy1, signal, LegStatus.OPEN.getKey());
        if (strategy1.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey()))
            strategy1.setStatus(Status.PENDING.getKey());
        else
            strategy1.setStatus(Status.LIVE.getKey());
        strategy1.setLastDeployedOn(today.format(formatter));
        strategyRepository.saveAndFlush(strategy1);
        newLegs.forEach(leg -> leg.setStrategy(strategy1));
        strategyLegRepository.saveAllAndFlush(newLegs);

        signal.setSignalLegs(newLegs);
        String underlying = strategy.getUnderlying().getName();
        IndexInstruments instrument = IndexInstruments.fromKey(underlying);
        MarketData marketData = marketDataFetch.getInstrumentData(Long.valueOf(instrument.getLabel()));

        SignalAdditions signalAdditions = new SignalAdditions();
        Long indexPrice = 0L;
        if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()) ||
                strategy.getAtmType().equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey())) {
            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy,strategy.getUnderlying().getName());
            indexPrice = (long) (syntheticPrice * AMOUNT_MULTIPLIER);
            signalAdditions.setEntryUnderlingPrice((int)((int)syntheticPrice* AMOUNT_MULTIPLIER));
            signal.setBaseIndexPrice(indexPrice);
            signalAdditions.setCurrentAtm(signalMapperDto.get(0).getMarketLiveDto().getSyntheticAtm());
            signalAdditions.setEntryUnderlingSpotPrice((int) (signalMapperDto.get(0).getMarketLiveDto().getSyntheticPrice()* AMOUNT_MULTIPLIER));
        }else {
            indexPrice = (long) (marketData.getLTP() * AMOUNT_MULTIPLIER);
            signal.setBaseIndexPrice(indexPrice);
            int atm = marketDataFetch.getATM(strategy.getUnderlying().getName(), (int) marketData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()));
            signalAdditions.setEntryUnderlingPrice((int)(signalMapperDto.get(0).getMarketLiveDto().getSpotPrice() * AMOUNT_MULTIPLIER));
            signalAdditions.setEntryUnderlingSpotPrice((int)(signalMapperDto.get(0).getMarketLiveDto().getSpotPrice() * AMOUNT_MULTIPLIER));
            signalAdditions.setCurrentAtm(atm);
        }
        signal.setSignalAdditions(signalAdditions);
        Signal orderResponse = signalRepository.saveAndFlush(signal);

        List<StrategyLeg> strategyLegs = new ArrayList<>(orderResponse.getSignalLegs());

        for (StrategyLeg dto : strategyLegs) {
            dto.setLegIdentifier("QO_" + orderResponse.getId() + "_" + dto.getId());
        }
        strategyLegRepository.saveAllAndFlush(strategyLegs);
        logger.info("Order response: " + orderResponse);
        String legNames = strategyLegs.stream()
                .filter(leg -> leg.getName() != null && leg.getBuySellFlag() != null)
                .map(leg -> leg.getBuySellFlag()+" " +leg.getNoOfLots() + " lots of " + leg.getName())
                .collect(Collectors.joining(", "));

        deploymentErrorService.saveStrategyUpdateLogs(strategy1, "Order triggered; placing orders: "+legNames);
        return orderResponse;
    }

    public List<StrategyLeg> createStrategyLegs(List<SignalMapperDto> mapperSignal, Strategy strategy, Signal signal, String legType) {
        logger.info("Creating strategy legs for signal: " + signal.getId());
        double premiumCapital = 0.0;
        List<StrategyLeg> newLegs = new ArrayList<>();
        Long indexPrice = 0L;
        String underlying = strategy.getUnderlying().getName();
        IndexInstruments instrument = IndexInstruments.fromKey(underlying);
        if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()) ||
                strategy.getAtmType().equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey())) {
            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy,strategy.getUnderlying().getName());
            indexPrice = (long) (syntheticPrice * AMOUNT_MULTIPLIER);
        }else {
            MarketData marketData = marketDataFetch.getInstrumentData(Long.valueOf(instrument.getLabel()));
            indexPrice = (long) (marketData.getLTP() * AMOUNT_MULTIPLIER);
        }

        for (SignalMapperDto dto : mapperSignal) {
            LegOrderDto newDto = new LegOrderDto();
            newDto.setName(dto.getLegName());
            newDto.setExchangeInstrumentID((long) dto.getTouchlineBinaryResposne().getExchangeInstrumentId());
            newDto.setPrice((long) (dto.getTouchlineBinaryResposne().getLTP() * DEFAULT_AMOUNT_INTERVAL));
            newDto.setQuantity((long) dto.getLots());
            newDto.setStatus(legType);
            newDto.setDeleteIndicator("N");
            newDto.setLotSize((long) dto.getMasterData().getLotSize());
            newDto.setBuySellFlag(dto.getBuySellFlag());
            newDto.setNoOfLots((long) dto.getLots());
            newDto.setLegType(dto.getLegType());
//            if (strategy.getStopLoss() != null) newDto.setStopLossUnitValue(strategy.getStopLoss());
//            if (strategy.getTarget() != null) newDto.setTargetUnitValue(strategy.getTarget());
            newDto.setTargetUnitType(dto.getTargetUnitType());
            newDto.setTargetUnitValue(dto.getTargetUnitValue());
            newDto.setTargetUnitToggle(dto.getTargetUnitToggle());
            newDto.setStopLossUnitToggle(dto.getStopLossUnitToggle());
            newDto.setStopLossUnitType(dto.getStopLossUnitType());
            newDto.setStopLossUnitValue(dto.getStopLossUnitValue());
            if(TOGGLE_TRUE.equalsIgnoreCase(dto.getTrailingStopLossToggle())) {
                newDto.setTrailingStopLossToggle(dto.getTrailingStopLossToggle());
                newDto.setTrailingStopLossType(dto.getTrailingStopLossType());
                newDto.setTrailingStopLossValue(dto.getTrailingStopLossValue());
                newDto.setTrailingDistance(dto.getTrailingDistance());
            }
            newDto.setOptionType(dto.getOptionType());
            newDto.setMultiOrdersFlag("y");
            newDto.setSegment(strategyUtils.getSegment(strategy.getUnderlying().getName()));
            newDto.setName(dto.getLegName());
            StrategyLeg leg = modelMapper.map(newDto, StrategyLeg.class);
            leg.setDerivativeType(dto.getDerivativeType());
            leg.setAppUser(strategy.getAppUser());
            leg.setUserAdmin(strategy.getUserAdmin());
            leg.setSignal(signal);

            leg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
            leg.setClosingPrice((long) (dto.getTouchlineBinaryResposne().getLTP() * AMOUNT_MULTIPLIER));
            leg.setConstantIV((long) (dto.getTouchlineBinaryResposne().getIV() * GREEK_MULTIPLIER));
            leg.setConstantDelta((long) (dto.getTouchlineBinaryResposne().getDelta() * GREEK_MULTIPLIER));
            leg.setLegType(LegStatus.TYPE_OPEN.getKey());
            leg.setLatestUpdatedQuantity((long) dto.getQuantity());
            leg.setQuantity((long) dto.getQuantity());
            leg.setDerivativeType(dto.getDerivativeType());
            leg.setLatestIndexPrice(indexPrice);
            leg.setBaseIndexPrice(indexPrice);

            if (strategy.getStrategyTag().equalsIgnoreCase(StrategyCategoryType.DIY.getKey())) {
                if (TOGGLE_TRUE.equalsIgnoreCase(leg.getTrailingStopLossToggle())) {
                    double traillingpoints = dto.getTouchlineBinaryResposne().getLTP() - dto.getTrailingDistance();
                    leg.setTrailingStopLossPoints((long) (traillingpoints * AMOUNT_MULTIPLIER));
                }
            }
            if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())){
                leg.setTradedPrice((long)dto.getTouchlineBinaryResposne().getLTP());
                leg.setFilledQuantity((long) dto.getQuantity());
                leg.setStatus(SignalStatus.LIVE.getKey());
                leg.setExecutedTime(Instant.now());
            }else {
                leg.setStatus(LegStatus.EXCHANGE.getKey());
                leg.setFilledQuantity(0L);
            }

            // Debug print
            logger.debug("Mapped Leg: " + leg);
            leg.setExecutedPrice(newDto.getPrice());
            newLegs.add(leg);
            premiumCapital = premiumCapital + (dto.getTouchlineBinaryResposne().getLTP()* dto.getLots() * dto.getMasterData().getLotSize());
        };
        assignStopLossPremiumCapital(strategy, premiumCapital);
        return newLegs;
    }

    private void assignStopLossPremiumCapital(Strategy strategy, double premiumCapital) {

        try {
            Long profitMtmUnitValue = null;
            Long stopLossMtmValue = null;
            ExitDetails exitDetails = strategy.getExitDetails();

            if (exitDetails.getTargetUnitToggle().equalsIgnoreCase(TOGGLE_TRUE)
                    && exitDetails.getTargetUnitType() != null) {

                profitMtmUnitValue = exitDetails.getTargetUnitValue() * strategy.getMultiplier();
                if(exitDetails.getTargetUnitType().equalsIgnoreCase(MtmMenu.PERCENT_OF_CAPITAL.getKey())) {
                    profitMtmUnitValue = Math.round((premiumCapital * exitDetails.getTargetUnitValue()) / 100.0);
                }
                exitDetails.setProfitMtmUnitValue(profitMtmUnitValue);
            }
            if (exitDetails.getStopLossUnitToggle().equalsIgnoreCase(TOGGLE_TRUE) && exitDetails.getStopLossUnitType() != null) {

                stopLossMtmValue = exitDetails.getStopLossUnitValue() * strategy.getMultiplier();
                if(exitDetails.getStopLossUnitType().equalsIgnoreCase(MtmMenu.PERCENT_OF_CAPITAL.getKey())) {
                    stopLossMtmValue = Math.round((premiumCapital * exitDetails.getStopLossUnitValue()) / 100.0);
                }
                exitDetails.setStoplossMtmUnitValue(stopLossMtmValue);
            }
            exitDetails.setPremiumCapital(Math.round(premiumCapital));

        }catch (Exception e){
            logger.error("Error while assignStopLossPremiumCapital strategy id: {}, exception = {}", strategy.getId(), e.getMessage());
        }
    }

    public List<StrategyLeg> createExitStrategyLegs(List<SignalMapperDto> mapperSignal, Strategy strategy, Signal signal) {
        logger.info("Creating exit strategy legs for signal ID: " + signal.getId() + " and strategy ID: " + strategy.getId());
        try{
            List<StrategyLeg> newLegs = mapperSignal.stream().map(dto -> {
                LegOrderDto newDto = new LegOrderDto();
                newDto.setName(dto.getName());
                newDto.setExchangeInstrumentID((long) dto.getTouchlineBinaryResposne().getExchangeInstrumentId());
                newDto.setPrice((long) (dto.getTouchlineBinaryResposne().getLTP() * DEFAULT_AMOUNT_INTERVAL));
                newDto.setQuantity((long) dto.getQuantity());
                newDto.setCreatedAt(Instant.now());
                newDto.setLotSize((long) dto.getMasterData().getLotSize());
                newDto.setStatus(LegStatus.EXCHANGE.getKey());
                newDto.setDeleteIndicator("N");
                newDto.setBuySellFlag(dto.getBuySellFlag());
                newDto.setNoOfLots((long) dto.getLots());
                newDto.setLegType(dto.getLegType());
//            if (strategy.getStopLoss() != null) newDto.setStopLossUnitValue(strategy.getStopLoss());
//            if (strategy.getTarget() != null) newDto.setTargetUnitValue(strategy.getTarget());
                newDto.setTargetUnitType(dto.getTargetUnitType());
                newDto.setTargetUnitValue(dto.getTargetUnitValue());
                newDto.setStopLossUnitType(dto.getStopLossUnitType());
                newDto.setStopLossUnitValue(dto.getStopLossUnitValue());
                if(TOGGLE_TRUE.equalsIgnoreCase(dto.getTrailingStopLossToggle())) {
                    newDto.setTrailingStopLossToggle(dto.getTrailingStopLossToggle());
                    newDto.setTrailingStopLossType(dto.getTrailingStopLossType());
                    newDto.setTrailingStopLossValue(dto.getTrailingStopLossValue());
                    newDto.setTrailingDistance(dto.getTrailingDistance());
                }
                newDto.setOptionType(dto.getPositionType());
                newDto.setMultiOrdersFlag("y");
                // has to change has to set in props level
                newDto.setSegment(strategyUtils.getSegment(strategy.getUnderlying().getName()));
                StrategyLeg leg = modelMapper.map(newDto, StrategyLeg.class);
                leg.setAppUser(strategy.getAppUser());
                leg.setDerivativeType(dto.getDerivativeType());
                leg.setUserAdmin(strategy.getUserAdmin());
                leg.setLatestUpdatedQuantity(newDto.getQuantity());
                leg.setOptionType(dto.getOptionType());
                leg.setSignal(signal);
                leg.setStrategy(strategy);
                leg.setLegType(LegStatus.EXIT.getKey());
                if (!dto.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.MANUALLY_TRADED.getKey())) {
                    leg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
                } else {
                    leg.setExchangeStatus(LegExchangeStatus.MANUALLY_TRADED.getKey());
                }
                leg.setExecutedPrice((long) (dto.getTouchlineBinaryResposne().getLTP() * AMOUNT_MULTIPLIER));
                leg.setSignal(signal);
                StrategyLeg finalLeg = new StrategyLeg();
                finalLeg.setFields(leg);

                logger.info("Mapped Leg: " + finalLeg);
                return finalLeg;
            }).collect(Collectors.toList());
            return newLegs;
        } catch (Exception e) {
            logger.error("Failed to create exit strategy legs for signal ID: " + signal.getId(), e);

            if (strategy.getUserAdmin() != null) {
                String errorDetails = "Failed to create exit strategy legs: " + e.getMessage();
//                String emailBody = emailService.getEmailErrorTemplate(errorDetails);
//                String toEmail = strategy.getUserAdmin().getEmail();
//                emailService.sendEmail(toEmail, "Exit Leg Creation Error", emailBody);
            }
            grpcErrorService.placingOrderLogs(ERROR_SIGNAL_PLACING_EXIT, signal, Status.ERROR.getKey());
            throw new RuntimeException("Error while creating exit strategy legs", e);
        }
    }


    public Signal createDiySignal(Strategy strategy) {
        try {
            logger.info("Creating signal for strategy: " + strategy.getName());
            //  Hibernate.initialize(strategy.getStrategyLeg());
            List<StrategyLeg> legs = strategyLegRepository.findByStrategyIdAndLegType(strategy.getId(), StrategyCategoryType.DIY.getKey());

            logger.info("legs size is : " + legs.size());
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

            List<SignalMapperDto> signalMapperDto = new ArrayList<>();
            legs.forEach(leg -> {
                if (leg.getDerivativeType() != null && leg.getDerivativeType().equalsIgnoreCase(OptionType.FUTURE.getKey())) {

                    String futurekey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) +
                            commonUtils.getExpiryShotDateByIndex(leg.getLegExpName(), strategy.getUnderlying().getName(), leg.getDerivativeType()) +
                            OptionType.FUTURE.getKey();
                    MasterResponseFO master = marketDataFetch.getMasterResponse(futurekey);
                    MarketData futurelivedata = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
                    SignalMapperDto signalMapperDto1 = diyStrategyCommonUtil.getSignalMapperDto(strategy, leg, master, futurelivedata, futurekey);
                    signalMapperDto1.setMarketLiveDto(marketLive);
                    signalMapperDto.add(signalMapperDto1);

                } else {
                    if (isPremiumOrDeltaSelection(leg.getSktSelection())) {
                        SignalMapperDto premiumDeltaDto = diyStrategyCommonUtil.handlePremiumDeltaStrikeSelection(strategy, leg);
                        if (premiumDeltaDto != null) {
                            premiumDeltaDto.setMarketLiveDto(marketLive);
                            signalMapperDto.add(premiumDeltaDto);
                        }
                    } else {
//                        Integer strike = diyStrategyCommonUtil.getDiyStrike(leg.getSktType(), strategy.getUnderlying().getName(), leg.getOptionType());
                        Integer strike = diyStrategyCommonUtil.getDiyStrike(strategy, leg);

                        String newKey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) +
                                commonUtils.getExpiryShotDateByIndex(leg.getLegExpName(), strategy.getUnderlying().getName(), leg.getDerivativeType()) +
                                "-" + strike + (leg.getOptionType().equalsIgnoreCase(SegmentType.CE.getKey()) ? "CE" : "PE");

                        logger.info("keys generated: "+newKey);
                        System.out.println("keys generated: "+newKey);
                        MasterResponseFO master = marketDataFetch.getMasterResponse(newKey);
                        MarketData touchline = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
                        SignalMapperDto signalMapperDto1 = diyStrategyCommonUtil.getSignalMapperDto(strategy, leg, master, touchline, newKey);
                        signalMapperDto1.setMarketLiveDto(marketLive);
                        signalMapperDto.add(signalMapperDto1);
                    }
                }

            });
            Signal signal = createSignal(strategy, signalMapperDto);


            return signal;
        } catch (Exception e) {
            logger.error("unable to createDiy signal for strategy "+strategy.getId(), e);
            errorCreatingSignal(strategy, e);
//            e.printStackTrace();
//            throw new RuntimeException(e.getMessage());
        }
        return null;
    }


    private boolean isPremiumOrDeltaSelection(String sktSelection) {
        return sktSelection != null &&
                (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_NEAREST.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_GREATERTHAN.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_LESSTHAN.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_NEAREST.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_GREATERTHAN.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_LESSTHAN.getKey()));
    }

    @Transactional
    public void errorCreatingSignal(Strategy strategy, Exception e) {
        try {
            //needs to add DeploymentError for the strategy
            strategy.setStatus(Status.ERROR.getKey());
            strategyRepository.save(strategy);
            strategyRepository.flush();
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrors.setStatus(RUN_TIME_EXCEPTION);
            deploymentErrors.setDescription(new ArrayList<>(List.of("Error: Failed to create a new signal due to missing data")));
            deploymentErrors.setErrorCode(e.getMessage());
            deploymentErrorsRepository.save(deploymentErrors);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }



    @Transactional(propagation = Propagation.REQUIRED ,rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Signal createExit(Strategy strategy1) {
        Optional<Strategy> strategy2 = strategyRepository.findById(strategy1.getId());
        Strategy strategy = strategy2.get();
        List<Signal> activeSignal =getActiveSignals(strategy.getId(), SignalStatus.LIVE.getKey());
        if (activeSignal.isEmpty()) {
            return null;
        } else {
            try {
                activeSignal.sort(Comparator.comparingInt(dto -> Math.toIntExact(dto.getId())));
                Optional<Signal> signalOptional = signalRepository.findByIdForUpdate(activeSignal.get(0).getId());
                if (signalOptional.isEmpty()) {
                    return null;
                }
                Signal signal = signalOptional.get();
                Hibernate.initialize(signal.getSignalLegs());
                List<StrategyLeg> signalLegs = signal.getSignalLegs().stream().filter(dto -> dto.getStatus().equalsIgnoreCase(LegStatus.OPEN.getKey()) ||
                        (dto.getLegType().equalsIgnoreCase(LegStatus.TYPE_OPEN.getKey())||
                                dto.getStatus().equalsIgnoreCase(PLACING_ORDER))&&
                                !dto.getStatus().equalsIgnoreCase(LegStatus.EXIT.getKey())).toList();
                List<SignalMapperDto> signalMapperDto = signalLegs.stream().map(dto -> {
                    SignalMapperDto newDto = new SignalMapperDto();
                    newDto.setName(dto.getName());
                    newDto.setLots(dto.getNoOfLots());
                    logger.info("--------- EXIT LOOP SIGNAL SERVICE ----------");
                    logger.info(dto.getName());
                    logger.info("--------- EXIT LOOP SIGNAL SERVICE  INSTRUMENT ID ----------");
                    logger.info(String.valueOf(dto.getExchangeInstrumentId()));
                    newDto.setMasterData(marketDataFetch.getMasterResponse(dto.getName()));
                    MarketData touchline = touchLineService.getTouchLine(String.valueOf(dto.getExchangeInstrumentId()));
                    newDto.setTouchlineBinaryResposne(touchline);
                    newDto.setBuySellFlag(dto.getBuySellFlag()  );
                    newDto.setOptionType(dto.getOptionType());
                    newDto.setLegType(dto.getLegType());
                    newDto.setDerivativeType(dto.getDerivativeType());
                    newDto.setQuantity(Math.toIntExact(dto.getFilledQuantity()));
                    newDto.setExchangeStatus(dto.getExchangeStatus());
                    return newDto;
                }).toList();

                List<StrategyLeg> legs = createExitStrategyLegs(signalMapperDto, strategy, signal);

                Long indexPrice = 0L;
                String underlying = strategy.getUnderlying().getName();
                IndexInstruments instrument = IndexInstruments.fromKey(underlying);
                MarketData marketData = marketDataFetch.getInstrumentData(Long.valueOf(instrument.getLabel()));

                if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey())) {
                    double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy,strategy.getUnderlying().getName());
                    indexPrice = (long) (syntheticPrice * AMOUNT_MULTIPLIER);
                }else {
                    indexPrice = (long) (marketData.getLTP() * AMOUNT_MULTIPLIER);
                    signal.setBaseIndexPrice(indexPrice);
                }

                Long finalIndexPrice = indexPrice;

                Hibernate.initialize(signal.getSignalAdditions());
                double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());
                logger.info("synthetic or Fair price is when creating the exit of the strategy  : "+syntheticPrice);

                List<StrategyLeg> oldlegs = new ArrayList<>(
                        signal.getSignalLegs().stream().map(dto -> {
                            if (dto.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                                dto.setStatus(LegStatus.EXIT.getKey());
                                dto.setLatestIndexPrice(finalIndexPrice);
                                dto.setLatestIndexPrice((long)syntheticPrice*AMOUNT_MULTIPLIER);
                            }
                            return dto;
                        }).toList()
                );

                List<StrategyLeg> oldSavedLegs =  strategyLegRepository.saveAll(oldlegs);
                List<StrategyLeg> newlegs =  strategyLegRepository.saveAll(legs);
                newlegs.stream().forEach((dto) -> {
                    logger.info("New leg created with ID: " + dto.getId() + " and Name: " + dto.getName());
                });
                if (!newlegs.isEmpty())  oldlegs.addAll(newlegs);
                try {

                    if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())){
                        strategy.setStatus(Status.EXIT_PENDING.getKey());
                    }else
                        strategy.setStatus(SignalStatus.EXIT.getKey());

                    strategyRepository.save(strategy);
                    Optional<Signal> signalOptionals = signalRepository.findByIdForUpdate(activeSignal.get(0).getId());
                    if (signalOptionals.isEmpty()) {
                        return null;
                    }
                    Signal signal1 = signalOptionals.get();

                    if (!newlegs.isEmpty())
                        signal1.getSignalLegs().addAll(newlegs);

                    signal1.setStatus(SignalStatus.EXIT.getKey());
                    Signal result = signalRepository.save(signal1);
                    signalRepository.flush();
                    String legNames = signal1.getSignalLegs().stream().filter(leg -> leg.getLegType().equalsIgnoreCase(LegType.OPEN.getKey()))
                            .map(leg -> (leg.getNoOfLots()) + " lots of " + leg.getName())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(", "));

                    deploymentErrorService.saveStrategyUpdateLogs(strategy, "Exit signal triggered; placing exit order for, "+legNames);

                    return result;
                }catch (Exception e){
                    logger.error(e.getMessage());
                    throw new RuntimeException(e.getMessage());
                }

            } catch (Exception e) {
                activeSignal.forEach(signal -> {
                    grpcErrorService.placingOrderLogs(ERROR_PLACING_EXIT_ORDER_DESCRIPTION, signal, Status.ERROR.getKey());
                });
                logger.error(e.getMessage());
            }
        }

        return null;
    }

    @Transactional
    public Signal createSingleExit(Strategy strategy, StrategyLeg strategyLeg) {
        Hibernate.initialize(strategyLeg.getSignal());
        Signal signal = signalRepository.findByIdForUpdate(strategyLeg.getSignal().getId()).orElse(null);
        if (signal == null) {
            logger.error("Signal not found for ID: " + strategyLeg.getSignal().getId());
            return null;
        }
        List<StrategyLeg> oldLegs = new CopyOnWriteArrayList<>(signal.getSignalLegs());
        StrategyLeg changeLeg = oldLegs.stream().filter(dto -> Objects.equals(dto.getId(), strategyLeg.getId())).findFirst().orElse(null);
        List<SignalMapperDto> signalMapperDto = Stream.of(changeLeg).map(dto -> {
            SignalMapperDto newDto = new SignalMapperDto();
            newDto.setLots(dto.getNoOfLots());
            newDto.setName(dto.getName());
            newDto.setBuySellFlag(dto.getBuySellFlag());
            newDto.setLegType(dto.getLegType());
            newDto.setDerivativeType(dto.getDerivativeType());
            newDto.setQuantity(Math.toIntExact(strategyLeg.getFilledQuantity()));
            newDto.setTargetUnitType(strategyLeg.getTargetUnitType());
            newDto.setTargetUnitValue(strategyLeg.getTargetUnitValue());
            newDto.setStopLossUnitType(strategyLeg.getStopLossUnitType());
            newDto.setStopLossUnitValue(strategyLeg.getStopLossUnitValue());
            newDto.setTrailingStopLossToggle(strategyLeg.getTrailingStopLossToggle());
            newDto.setTrailingStopLossType(strategyLeg.getTrailingStopLossType());
            newDto.setTrailingStopLossValue(strategyLeg.getTrailingStopLossValue());
            newDto.setTrailingDistance(strategyLeg.getTrailingDistance());
            newDto.setPositionType(strategyLeg.getSignal().getPositionType());
            newDto.setOptionType(strategyLeg.getOptionType());
            newDto.setMasterData(marketDataFetch.getMasterResponse(dto.getName()));
            MarketData touchline = touchLineService.getTouchLine(String.valueOf(dto.getExchangeInstrumentId()));
            newDto.setTouchlineBinaryResposne(touchline);
            newDto.setExchangeStatus(dto.getExchangeStatus());
            return newDto;
        }).toList();
        List<StrategyLeg> legs = createExitStrategyLegs(signalMapperDto, strategy, signal);
        Hibernate.initialize(signal.getSignalAdditions());
        double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());

        changeLeg.setLatestIndexPrice((long) syntheticPrice * AMOUNT_MULTIPLIER);
        changeLeg.setStatus(LegStatus.EXIT.getKey());
        strategyLegRepository.save(changeLeg);
        List<StrategyLeg> savedNewLegs = strategyLegRepository.saveAll(legs);
        for (StrategyLeg leg : savedNewLegs) {
            leg.setLegIdentifier("QO_" + signal.getId() + "_" + leg.getId());
        }
        savedNewLegs = strategyLegRepository.saveAll(legs);
        // Refetch the signal for safe update (prevents CME)
        Optional<Signal> signalOpt = signalRepository.findByIdForUpdate(signal.getId());
        if (signalOpt.isEmpty()) {
            logger.error("Signal not found for ID: " + signal.getId());
            return null;
        }

        Signal updatedSignal = signalOpt.get();
        Hibernate.initialize(updatedSignal.getSignalLegs());

        List<StrategyLeg> mergedLegs = new ArrayList<>(updatedSignal.getSignalLegs());
        mergedLegs.addAll(savedNewLegs);
        updatedSignal.setSignalLegs(mergedLegs);

        // Save and return updated signal
        return signalRepository.save(updatedSignal);
    }


    @Transactional
    public Signal createSingleExitWithList(Strategy strategy, List<StrategyLeg> strategyLegs , Signal signal) {

        if (signal == null) {
            logger.error("Signal not found for strategy ID: {} " ,strategy.getId());
            return null;
        }

        List<StrategyLeg> changeLegs = new ArrayList<>(strategyLegs.stream().filter(dto-> dto.getStatus().equalsIgnoreCase(LegStatus.EXCHANGE.getKey())).collect(Collectors.toList()));
        List<SignalMapperDto> signalMapperDto = changeLegs.stream().map(dto -> {
            SignalMapperDto newDto = new SignalMapperDto();
            newDto.setLots(dto.getNoOfLots());
            newDto.setBuySellFlag(dto.getBuySellFlag());
            newDto.setLegType(dto.getLegType());
            newDto.setDerivativeType(dto.getDerivativeType());
            newDto.setQuantity(Math.toIntExact(dto.getFilledQuantity()));
            newDto.setTargetUnitType(dto.getTargetUnitType());
            newDto.setTargetUnitValue(dto.getTargetUnitValue());
            newDto.setStopLossUnitType(dto.getStopLossUnitType());
            newDto.setStopLossUnitValue(dto.getStopLossUnitValue());
            newDto.setTrailingStopLossToggle(dto.getTrailingStopLossToggle());
            newDto.setTrailingStopLossType(dto.getTrailingStopLossType());
            newDto.setTrailingStopLossValue(dto.getTrailingStopLossValue());
            newDto.setTrailingDistance(dto.getTrailingDistance());
            newDto.setPositionType(dto.getSignal().getPositionType());
            newDto.setOptionType(dto.getOptionType());
            newDto.setMasterData(marketDataFetch.getMasterResponse(dto.getName()));
            MarketData touchline = touchLineService.getTouchLine(String.valueOf(dto.getExchangeInstrumentId()));
            newDto.setTouchlineBinaryResposne(touchline);
            newDto.setExchangeStatus(dto.getExchangeStatus());
            return newDto;
        }).toList();
        List<StrategyLeg> legs = createExitStrategyLegs(signalMapperDto, strategy, signal);
        Hibernate.initialize(signal.getSignalAdditions());
        double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());

        List<StrategyLeg> newChangeLeg = new ArrayList<>( changeLegs.stream().map(dto -> {
            dto.setLatestIndexPrice((long) syntheticPrice * AMOUNT_MULTIPLIER);
            dto.setStatus(LegStatus.EXIT.getKey());
            return dto;
        }).collect(Collectors.toList()));


        strategyLegRepository.saveAll(newChangeLeg);
        List<StrategyLeg> savedNewLegs = strategyLegRepository.saveAll(legs);
        Optional<Signal> signalOpt = signalRepository.findByIdForUpdate(signal.getId());
        if (signalOpt.isEmpty()) {
            logger.error("Signal not found for ID: " + signal.getId());
            return null;
        }

        Signal updatedSignal = signalOpt.get();
        Hibernate.initialize(updatedSignal.getSignalLegs());

        List<StrategyLeg> mergedLegs = new ArrayList<>(updatedSignal.getSignalLegs());
        mergedLegs.addAll(savedNewLegs);
        updatedSignal.setSignalLegs(mergedLegs);
        // Save and return updated signal
        return signalRepository.save(updatedSignal);
    }

    @Transactional
    public List<Signal> getActiveSignals(Long strategyId, String status) {
        return signalRepository.findByStrategyIdAndStatus(strategyId, status);
    }

}
