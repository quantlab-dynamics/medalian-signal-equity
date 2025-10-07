//package com.quantlab.client.websockets;
//
//import com.quantlab.client.dto.GreeksDTO;
//import com.quantlab.common.dto.SignalPNLDTO;
//import com.quantlab.common.dto.StrategyLegPNLDTO;
//import com.quantlab.client.service.UserStrategyService;
//import com.quantlab.common.dto.StrategyPNLDto;
//import com.quantlab.common.repository.*;
//import com.quantlab.common.utils.staticstore.IndexInstruments;
//import com.quantlab.common.utils.staticstore.dropdownutils.*;
//import com.quantlab.common.utils.staticstore.dropdownutils.Status;
//import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
//import com.quantlab.signal.dto.*;
//import com.quantlab.signal.dto.redisDto.MarketData;
//import com.quantlab.signal.service.redisService.TouchLineService;
//import com.quantlab.signal.utils.CommonUtils;
//import com.quantlab.signal.web.service.MarketDataFetch;
//import jakarta.annotation.PostConstruct;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.*;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.stream.Collectors;
//
//import static com.quantlab.common.utils.staticstore.AppConstants.*;
//import static com.quantlab.signal.utils.staticdata.StaticStore.roundToTwoDecimalPlaces;
//
//@Service
//@Transactional
//public class SocketDataProcessorService {
//
//    private static final Logger logger = LoggerFactory.getLogger(SocketDataProcessorService.class);
//
//    @Autowired
//    StrategyRepository strategyRepository;
//
//    @Autowired
//    SignalRepository signalRepository;
//
//    @Autowired
//    SignalAdditionsRepository signalAdditionsRepository;
//
//    @Autowired
//    AppUserRepository appUserRepository;
//
//    @Autowired
//    StrategyLegRepository strategyLegRepository;
//
//    @Autowired
//    MarketDataFetch marketDataFetch;
//
//    @Autowired
//    TouchLineService touchLineService;
//
//    @Autowired
//    UserStrategyService strategyService;
//
//    @Autowired
//    DeploymentErrorsRepository deploymentErrorsRepository;
//
//    @Autowired
//    CommonUtils commonUtils;
//
//    @Value("${threads.pool.Pnl}")
//    int threads;
//
//    private final AtomicBoolean isScheduled = new AtomicBoolean(false);
//    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(6);
//    public static Set<String> subscribedAppUsers = ConcurrentHashMap.newKeySet();
//    private static ConcurrentHashMap<String, PNLHoldingDTO> userIdPositionHoldings = new ConcurrentHashMap<>();
//    List<SignalPNLDTO> previousLiveSignals = new ArrayList<>();
//    private final ConcurrentHashMap<Long, Double> strategyNonLivePNL = new ConcurrentHashMap<>();
//    private final ConcurrentHashMap<Long, Double> allIndexPrices = new ConcurrentHashMap<>();
//    private final ConcurrentHashMap<Long, Double> allSyntheticIndexPrices = new ConcurrentHashMap<>();
//    int liveThreads = 0;
//
////    private final Set<Long> usersMetExitConditions = Collections.synchronizedSet(new HashSet<>());
//
//    OpenPositionsSocketHandler openPositionsSocketHandler = new OpenPositionsSocketHandler();
//
//    @PostConstruct
//    public void start() {
//        try {
//            startProcessing("1"); // Starting with a default user
//        } catch (Exception e) {
//            logger.error("Error during initialization of SocketDataProcessorService: {}", e.getMessage());
//        }
//    }
//
//
//    @Transactional
//    public void startProcessing(String userId) {
//        try {
//            subscribedAppUsers.add(userId);
//            while (liveThreads < threads) {
//                Runnable[] taskHolder = new Runnable[1];
//                taskHolder[0] = () -> {
//                    try {
//                        if (LocalTime.now().isAfter(LocalTime.of(9, 15)) && LocalTime.now().isBefore(LocalTime.of(15, 31))) {
//                            fetchDTO();
//                            if (!subscribedAppUsers.isEmpty()) {
//                                sendProcessedDTOs(true);
//                            }
//                        } else {
//                            sendProcessedDTOs(false);
//                            Thread.sleep(1000);
//                        }
//                    } catch (Exception e) {
//                        logger.error("Error processing task: {}", e.getMessage());
//                    } finally {
//                        scheduler.submit(taskHolder[0]);
//                    }
//                };
//                scheduler.submit(taskHolder[0]);
//                liveThreads++;
//            }
//        } catch (Exception e) {
//            logger.error("Error in P&L socket startProcessing: {}", e.getMessage());
//        }
//    }
//
//    private void stopProcessing() {
//        scheduler.shutdown();
//    }
//
//   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
//    public void fetchDTO() {
//        try {
//            ConcurrentHashMap<Long, GreeksDTO> instrumentGreeksDTO = new ConcurrentHashMap<>();
//            List<SignalPNLDTO> allLiveSignals =  fetchSignalDTOs();
//            fetchAllIndexesPrice();
//            List<StrategyLegPNLDTO> savingStrategyLegsDTOList = new ArrayList<>();
//            HashMap<String,HashMap<Long,StrategyLegTableDTO>> mappingLegsToUsers = new HashMap<>();
//            if(allLiveSignals.size() != previousLiveSignals.size() || signalsRemainSame(allLiveSignals)){
//                differentSignals(allLiveSignals);
//            }
//            for (SignalPNLDTO signalDTO : allLiveSignals) {
//                StrategyLegTableDTO strategyIdAndLegs = new StrategyLegTableDTO();
//                List<Object[]> signalLegsResponse = strategyLegRepository.findLegsBySignalIdAndStatus(signalDTO.getId(), LegStatus.OPEN.getKey());
//                List<StrategyLegPNLDTO> signalLegs = mapToStrategyLegPNLDTOList(signalLegsResponse);
//
//                ArrayList<LegHoldingDTO> eachStrategyRows = new ArrayList<>();
//                double signalPAndL = 0.0;
//                StrategyPNLDto strategyDto = getStrategyDetails(signalDTO.getId());strategyIdAndLegs.setStrategyId(strategyDto.getId());
//                Long indexPrice = fetchIndexPrice(signalDTO, strategyDto, strategyIdAndLegs);
//
//                for (StrategyLegPNLDTO strategyLeg : signalLegs) {
//                    if (VALID_LEG_FOR_PNL.contains(strategyLeg.getStatus()) && LegType.OPEN.getKey().equalsIgnoreCase(strategyLeg.getLegType())) {
//                        GreeksDTO ltpAndGreeks = instrumentGreeksDTO.get(strategyLeg.getExchangeInstrumentId());
//                        if (ltpAndGreeks == null){
//                            ltpAndGreeks = fetchTouchlineBinaryResponseLTP(strategyLeg.getExchangeInstrumentId());
//                            if (ltpAndGreeks == null){
//                                logger.error("GreeksDTO is null for instrument ID: " + strategyLeg.getExchangeInstrumentId());
//                                continue;
//                            }
//                            instrumentGreeksDTO.put(strategyLeg.getExchangeInstrumentId(), ltpAndGreeks);
//                        }
//                        double legPAndL = processPNL(strategyLeg, ltpAndGreeks.getCurrentLegLTP(), strategyLeg.getExecutedPrice());
//                        signalPAndL = signalPAndL + (legPAndL);
//                        strategyLeg.setLtp((long) (ltpAndGreeks.getCurrentLegLTP() * AMOUNT_MULTIPLIER));
//                        strategyLeg.setProfitLoss((long) (legPAndL * AMOUNT_MULTIPLIER));
//                        strategyLeg.setCurrentIV((long) (ltpAndGreeks.getCurrentIV() * GREEK_MULTIPLIER));
//                        strategyLeg.setCurrentDelta((long) (ltpAndGreeks.getCurrentDelta() * GREEK_MULTIPLIER));
//                        strategyLeg.setLatestIndexPrice(indexPrice);
//                        LegHoldingDTO tableRow = new LegHoldingDTO(strategyLeg, ltpAndGreeks.getCurrentLegLTP(), signalDTO);
////                           tableRow.setMtm(processPNL(strategyLeg, ltpAndGreeks.getCurrentLegLTP(), strategyLeg.getClosingPrice()));
//                        eachStrategyRows.add(tableRow);
//                        savingStrategyLegsDTOList.add(strategyLeg);
//                    }
//                }
//                if (signalPAndL != 0.0) {
//                    signalDTO.setProfitLoss((long) (signalPAndL * AMOUNT_MULTIPLIER));//leg P&L total
//                }
//                strategyIdAndLegs.setStrategyStatus(strategyDto.getStatus());
//                double strategyPNLSum = sumStrategyNotLiveSignalsPNL(strategyDto.getId(), signalPAndL);
//                strategyIdAndLegs.setStrategyMTM(roundToTwoDecimalPlaces(strategyPNLSum));
////                fetchIndexPrice(signalDTO, strategyDto, strategyIdAndLegs);
//                strategyIdAndLegs.setData(eachStrategyRows);
//                updatedDataToEachUser(signalDTO, strategyDto, strategyIdAndLegs, mappingLegsToUsers);
//            }
//            mapToPNLHoldingDTO(mappingLegsToUsers);
//            saveLegsInDB(savingStrategyLegsDTOList, allLiveSignals);
//        } catch (Exception e) {
//            logger.error("error in P&L socket "+e.toString());
////            e.printStackTrace();
//        }
//    }
//
//    private void differentSignals(List<SignalPNLDTO> allLiveSignals) {
//        setFinalPNL();
//        previousLiveSignals = allLiveSignals;
//        findUsersNonLiveSignalsPnL();
//    }
//
//    @Transactional
//    public void setFinalPNL() {
//        try {
//            List<Long> exitedSignals = signalRepository.findRecentlyExitedSignalIds(SignalStatus.EXIT.getKey(), SignalStatus.EXIT.getKey());
//
//            for (Long signalId : exitedSignals) {
//                long signalPNL = 0L;
//                boolean hasValidLegs = false;
//
//                List<Object[]> nonExitLegsResponse = strategyLegRepository.findLegsBySignalIdAndLegType(signalId,LegType.OPEN.getKey());
//                List<StrategyLegPNLDTO> nonExitLegs = mapToStrategyLegPNLDTOList(nonExitLegsResponse);
//                List<Object[]> results = strategyLegRepository.findExitLegIdAndPricePairs(signalId);
//                Map<Long, Long> instrumentLTP = mapToInstrumentIdAndPNL(results);
//
//                for (StrategyLegPNLDTO leg: nonExitLegs){
//                    hasValidLegs = true;
//                    Long executedPrice = instrumentLTP.get(leg.getExchangeInstrumentId());
//                    if (executedPrice != null) {
//                        long legPNL = (long) processPNL(leg, executedPrice / (double) AMOUNT_MULTIPLIER, leg.getPrice()) * AMOUNT_MULTIPLIER;
//                        strategyLegRepository.updateProfitLoss(signalId, leg.getExchangeInstrumentId(), legPNL, LegStatus.TYPE_EXIT.getKey());
//                        signalPNL = signalPNL + legPNL;
//                    }
//                }
//                if (hasValidLegs)
//                    signalRepository.updateSignalProfitAndLastPNL(signalId,signalPNL,SignalStatus.EXIT.getKey());
//            }
//        } catch (Exception e) {
//            logger.error("error in PNL socket setFinalPNL : " + e.getMessage());
//        }
//    }
//
//
//    private void fetchAllIndexesPrice() {
//        for(IndexInstruments indexInstruments :IndexInstruments.values()){
//            GreeksDTO greeksDTO = fetchTouchlineBinaryResponseLTP(indexInstruments.getLabel().longValue());
//            if (greeksDTO != null)
//                allIndexPrices.put(indexInstruments.getLabel().longValue(),greeksDTO.getCurrentLegLTP());
//        }
//
//        for(IndexInstruments indexInstruments :IndexInstruments.values()){
//           Double syntheticPrice = marketDataFetch.getSyntheticPrice(null, indexInstruments.getKey());
//            if (syntheticPrice != null)
//                allSyntheticIndexPrices.put(indexInstruments.getLabel().longValue(),syntheticPrice);
//        }
//    }
//
//    public Long fetchIndexPrice(SignalPNLDTO signalDto, StrategyPNLDto strategyDTO, StrategyLegTableDTO strategyIdAndLegs) {
//        Double ltp;
//        String underlying = strategyDTO.getUnderlyingName();
//        IndexInstruments instrument = IndexInstruments.fromKey(underlying);
//
//        if (strategyDTO.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()))
//            ltp = allSyntheticIndexPrices.get(instrument.getLabel().longValue());
//        else
//            ltp = allIndexPrices.get(instrument.getLabel().longValue());
//
//        if (ltp == null)
//            return 0L;
//
//        strategyIdAndLegs.setIndexCurrentPrice(ltp);
//        signalDto.setLatestIndexPrice((long) (ltp*AMOUNT_MULTIPLIER));
//        return (long) (ltp*AMOUNT_MULTIPLIER);
//    }
//
//    private double sumStrategyNotLiveSignalsPNL(Long strategyId, double signalPAndL) {
//        Double strategyPNLSumLong = strategyNonLivePNL.get(strategyId);
//        return strategyPNLSumLong + signalPAndL;
//    }
//
//    public void mapToPNLHoldingDTO(HashMap<String, HashMap<Long, StrategyLegTableDTO>> mappingLegsToUsers) {
//        for (String userId : subscribedAppUsers) {
//            HashMap<Long, StrategyLegTableDTO> eachUserStrategies = mappingLegsToUsers.get(userId);
//            PNLHoldingDTO pnlHoldingDTO = fetchHeadersData(Long.valueOf(userId));
//            if (eachUserStrategies != null) {
//                HoldingsDTO holdingsDTO = new HoldingsDTO();
//                ArrayList<StrategyLegTableDTO> valuesList = new ArrayList<>(eachUserStrategies.values());
//                holdingsDTO.setStrategyLegs(valuesList);
//                pnlHoldingDTO.setStrategyLegs(holdingsDTO.getStrategyLegs());
//            }
//                userIdPositionHoldings.put(userId, pnlHoldingDTO);
//            }
//    }
//
//    public void updatedDataToEachUser(SignalPNLDTO signalDto, StrategyPNLDto strategyDto, StrategyLegTableDTO strategyIdAndLegs, HashMap<String,HashMap<Long,StrategyLegTableDTO>> mappingLegsToUsers) {
//        HashMap<Long,StrategyLegTableDTO> userStrategyLegTableDTOHashMap = new HashMap<>();
//
//        if (!mappingLegsToUsers.containsKey(signalDto.getUserId().toString())){
//            userStrategyLegTableDTOHashMap.put(strategyDto.getId(),strategyIdAndLegs);
//            mappingLegsToUsers.put(signalDto.getUserId().toString(),userStrategyLegTableDTOHashMap);
//        }else {
//            userStrategyLegTableDTOHashMap = mappingLegsToUsers.get(signalDto.getUserId().toString());
//            if (userStrategyLegTableDTOHashMap.containsKey(strategyDto.getId())){
//
//                userStrategyLegTableDTOHashMap.get(strategyDto.getId()).getData()
//                        .addAll(strategyIdAndLegs.getData());
//            }
//            else {
//                userStrategyLegTableDTOHashMap.put(strategyDto.getId(),strategyIdAndLegs);
//            }
//        }
//        mappingLegsToUsers.put(signalDto.getUserId().toString()
//                ,userStrategyLegTableDTOHashMap);
//
//    }
//
//    @Async
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void saveLegsInDB(List<StrategyLegPNLDTO> savingStrategyLegsDTOList,  List<SignalPNLDTO> allLiveSignals) {
//        try {
//            allLiveSignals.forEach((signal) -> signalRepository.updateProfitLossAndIndexNowById(signal.getId(), signal.getProfitLoss(),signal.getLatestIndexPrice()));
//            if (!savingStrategyLegsDTOList.isEmpty()) {
//                for (StrategyLegPNLDTO dto: savingStrategyLegsDTOList) {
//                    strategyLegRepository.updateRealtimeLegData(
//                            dto.getId(), dto.getLtp(), dto.getProfitLoss(),
//                            dto.getCurrentIV(), dto.getCurrentDelta());
//                }
//            }
//        } catch (Exception e) {
//            logger.error("error saving data in P&L socket "+e.getMessage());
//        }
//    }
//
//    private double processPNL(StrategyLegPNLDTO strategyLeg, double ltp, Long actualPrice) {
//        if (actualPrice != null) {
//            if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.BUY.getKey()))
//                return (ltp - (actualPrice / (double) AMOUNT_MULTIPLIER)) * strategyLeg.getFilledQuantity();
//            else
//                return ((actualPrice / (double) AMOUNT_MULTIPLIER) - ltp) * strategyLeg.getFilledQuantity();
//        }
//        return 0;
//    }
//
//    public void sendProcessedDTOs(Boolean liveMarket) {
//
//        try {
//            for (String userId: subscribedAppUsers){
//                PNLHoldingDTO holdingsDTO = userIdPositionHoldings.get(userId);
//                if (holdingsDTO != null && liveMarket) {
//                    openPositionsSocketHandler.sendOpenPositionsToUser(holdingsDTO.getUserID(), holdingsDTO);
//                }
//                else {
//                    holdingsDTO = fetchHeadersData(Long.valueOf(userId));
//                    openPositionsSocketHandler.sendOpenPositionsToUser(holdingsDTO.getUserID(), holdingsDTO);
//                }
//            }
//        } catch (Exception e) {
//            logger.error("error in P&L socket thrown in sendProcessedDTO(): "+ e.getMessage());
////            e.printStackTrace();
//        }
//    }
//
//    public GreeksDTO fetchTouchlineBinaryResponseLTP(Long instrumentId){
//            GreeksDTO greeksDTO = new GreeksDTO();
//        try {
//             MarketData marketData = marketDataFetch.getInstrumentData(instrumentId);
//            if (marketData != null) {
//                greeksDTO.setCurrentLegLTP(marketData.getLTP());
//                greeksDTO.setCurrentIV(marketData.getIV());
//                greeksDTO.setCurrentDelta(marketData.getDelta());
//            }else
//                return null;
//        }catch (Exception e){
//            logger.error("error in P&L socket fetching the redisData for the instrumentId = "+instrumentId+e.getMessage());
//        }
//        return greeksDTO;
//    }
//
//    public PNLHoldingDTO fetchHeadersData(Long userId){
//        PNLHoldingDTO liveHeaders =  fetchDataWhenUserHasNoSignalsLive(userId, ExecutionTypeMenu.LIVE_TRADING.getKey());
//        PNLHoldingDTO forwardHeaders = fetchDataWhenUserHasNoSignalsLive(userId, ExecutionTypeMenu.PAPER_TRADING.getKey());
//        PNLHoldingDTO pnlHoldingDTO = new PNLHoldingDTO();
//        PNLHeaderDTO livePnlHeaderDTO =  createHeaders(liveHeaders);
//        PNLHeaderDTO forwardPnlHeaderDTO = createHeaders(forwardHeaders);
//        pnlHoldingDTO.setLiveHeaders(livePnlHeaderDTO);
//        pnlHoldingDTO.setForwardHeaders(forwardPnlHeaderDTO);
//        pnlHoldingDTO.setUserID(String.valueOf(userId));
//        pnlHoldingDTO.setDeployedCapital(liveHeaders.getDeployedCapital() + forwardHeaders.getDeployedCapital());
//        pnlHoldingDTO.setPostionalPAndL(liveHeaders.getPostionalPAndL() + forwardHeaders.getPostionalPAndL());
//        pnlHoldingDTO.setIntradayPAndL(liveHeaders.getIntradayPAndL() + forwardHeaders.getIntradayPAndL());
//        pnlHoldingDTO.setTodaysPAndL(liveHeaders.getTodaysPAndL() + forwardHeaders.getTodaysPAndL());
//        pnlHoldingDTO.setOverAllUserPAndL(liveHeaders.getOverAllUserPAndL() + forwardHeaders.getOverAllUserPAndL());
//
//        return pnlHoldingDTO;
//    }
//
//    public PNLHeaderDTO createHeaders(PNLHoldingDTO forwardHeaders) {
//        PNLHeaderDTO pnlHeaderDTO = new PNLHeaderDTO();
//        pnlHeaderDTO.setTodaysPAndL(forwardHeaders.getTodaysPAndL());
//        pnlHeaderDTO.setOverAllUserPAndL(forwardHeaders.getOverAllUserPAndL());
//        pnlHeaderDTO.setIntradayPAndL(forwardHeaders.getIntradayPAndL());
//        pnlHeaderDTO.setPositionalPAndL(forwardHeaders.getPostionalPAndL());
//        pnlHeaderDTO.setDeployedCapital(forwardHeaders.getDeployedCapital());
//        return pnlHeaderDTO;
//    }
//
//
//    @Transactional(readOnly = true)
//    public PNLHoldingDTO fetchDataWhenUserHasNoSignalsLive(Long userId, String executionType) {
//
//        try {
//            Long storeTotal = signalRepository.findAllByCreatedAtTodayAndAppUsers(userId, executionType);
//            Long overAll = signalRepository.findOverallUserPAndL(userId, executionType);
//            Double todaysPAndL = storeTotal != null ? (storeTotal / (double) AMOUNT_MULTIPLIER) : 0.0;
//            Double overUserPAndL = overAll != null ? (overAll / (double) AMOUNT_MULTIPLIER) : 0.0;
//            Double positionalPAndL = 0.0;
//            Double intradayPAndL = 0.0;
//            Double deployedCapital =0.0;
//            Long value = signalRepository.findByExecutionIntraDayPAndL(userId, StrategyType.INTRADAY.getKey(), SubscriptionStatus.START.getKey(), executionType);
//            intradayPAndL = value != null ? (value / (double) AMOUNT_MULTIPLIER) : 0.0;
//            value = signalRepository.findByExecutionPositionalPAndL(userId, StrategyType.POSITIONAL.getKey(), Status.LIVE.getKey(), SubscriptionStatus.START.getKey(), executionType);
//            positionalPAndL = value != null ? (value / (double) AMOUNT_MULTIPLIER) : 0.0;
//            value = strategyRepository.fetchDeployedStrategiesCapitalByUserId(userId, SubscriptionStatus.START.getKey(), executionType);
//            deployedCapital = value != null ? (value / (double) AMOUNT_MULTIPLIER) : 0.0;
//            // converting the holdingsDTO to PNLHoldingDTO because we need to decrease size
//            PNLHoldingDTO holdingsDTO = new PNLHoldingDTO();
//            holdingsDTO.setTodaysPAndL(roundToTwoDecimalPlaces(todaysPAndL)); //Today generated signal P&L
//            holdingsDTO.setOverAllUserPAndL(roundToTwoDecimalPlaces(overUserPAndL)); //users all strategy signals generated P&L
//            holdingsDTO.setIntradayPAndL(roundToTwoDecimalPlaces(intradayPAndL));//strategy intraday P&L
//            holdingsDTO.setPostionalPAndL(roundToTwoDecimalPlaces(positionalPAndL));//strategy positional P&L
//            holdingsDTO.setDeployedCapital(roundToTwoDecimalPlaces(deployedCapital));
//            return holdingsDTO;
//        }catch (Exception e){
//            logger.error("Error in fetchDataWhenUserHasNoSignalsLive when processing for user: "+userId+" ,"+e.getMessage());
//        }
//        return null;
//    }
//
//
//
//    public boolean signalsRemainSame(List<SignalPNLDTO> latestLiveSignals) {
//        Set<Long> ids1 = previousLiveSignals.stream().map(SignalPNLDTO::getId).collect(Collectors.toSet());
//        Set<Long> ids2 = latestLiveSignals.stream().map(SignalPNLDTO::getId).collect(Collectors.toSet());
//        return ids1.equals(ids2);
//    }
//
//    @Transactional(readOnly = true)
//    public void findUsersNonLiveSignalsPnL() {
//        for (SignalPNLDTO signalPNLDTO: previousLiveSignals){
//            Long nonLivePNL = signalRepository.findTotalPNLByNonLiveSignalsToday(Status.LIVE.getKey(), signalPNLDTO.getStrategyId());
//            strategyNonLivePNL.put(signalPNLDTO.getStrategyId(), nonLivePNL != null?nonLivePNL/(double)AMOUNT_MULTIPLIER : 0);
//        }
//
//    }
//
//    public List<SignalPNLDTO> fetchSignalDTOs() {
//        List<Object[]> rows = signalRepository.findSignalDetails(StrategyType.POSITIONAL.getKey(), StrategyType.INTRADAY.getKey(), Status.LIVE.getKey());
//        return rows.stream()
//                .map(r -> new SignalPNLDTO(
//                        ((Number) r[0]).longValue(),
//                        ((Number) r[1]).longValue(),
//                        r[2] != null? ((Number) r[2]).longValue() :0,
//                        ((Number) r[3]).longValue(),
//                        r[4] != null? ((Number) r[4]).longValue() :0,
//                        r[5] != null? ((Number) r[5]).longValue() :0,
//                        r[6] != null? ((Number) r[6]).longValue() :0))
//                .collect(Collectors.toList());
//    }
//
//
//    public StrategyPNLDto getStrategyDetails(Long signalId) {
//        Object result = strategyRepository.findStrategyPNLDtoBySignalId(signalId);
//        Object[] row = (Object[]) result;  // Correct casting here
//
//        return new StrategyPNLDto(
//                ((Number) row[0]).longValue(),
//                (String) row[1],
//                (String) row[2],
//                (String) row[3],
//                (String) row[4],
//                ((Number) row[5]).longValue()
//        );
//    }
//
//
//    public List<StrategyLegPNLDTO> mapToStrategyLegPNLDTOList(List<Object[]> results) {
//        return results.stream()
//                .map(row -> new StrategyLegPNLDTO(
//                        getLong(row[0]),  // id
//                        getLong(row[1]),  // ltp
//                        getLong(row[2]),  // profitLoss
//                        getLong(row[3]),  // currentIV
//                        getLong(row[4]),  // currentDelta
//                        (String) row[5],  // buySellFlag
//                        getLong(row[6]),  // filledQuantity
//                        getLong(row[7]),  // price
//                        (String) row[8],  // name
//                        (String) row[9],  // status
//                        (String) row[10], // legType
//                        getLong(row[11]), // lotSize
//                        getLong(row[12]), // noOfLots
//                        getLong(row[13]), // signalId
//                        getLong(row[14]), // exchangeInstrumentId
//                        getLong(row[15]), // executedPrice
//                        getLong(row[16]), // constantIV
//                        getLong(row[17]), // constantDelta
//                        getLong(row[18]), // latestIndexPrice
//                        getLong(row[19])  // baseIndexPrice
//                ))
//                .collect(Collectors.toList());
//    }
//
//    private Long getLong(Object obj) {
//        return obj != null ? ((Number) obj).longValue() : null;
//    }
//
//    private Map<Long, Long> mapToInstrumentIdAndPNL(List<Object[]> results ){
//        return results.stream()
//                .collect(Collectors.toMap(
//                        row -> (Long) row[0],
//                        row -> (Long) row[1]
//                ));
//
//    }
//
//    public List<Map<String, Object>> fetchLiveIndexData() {
//        List<Map<String, Object>> indexDataList = new ArrayList<>();
//
//        Map<String, String> indexKeyMap = new LinkedHashMap<>();
//        indexKeyMap.put("Nifty 50", "26000");
//        indexKeyMap.put("Nifty Bank", "26001");
//        indexKeyMap.put("Sensex", "26065");
//
//        List<String> keys = new ArrayList<>(indexKeyMap.values());
//
//        Map<String, MarketData> marketDataMap = touchLineService.getMultipleTouchLines(keys);
//
//        for (Map.Entry<String, String> entry : indexKeyMap.entrySet()) {
//            String indexName = entry.getKey();
//            String key = entry.getValue();
//
//            MarketData marketData = marketDataMap.get("MD_" + key);
//
//            if (marketData != null) {
//                double ltp = marketData.getLTP();
//                double close = marketData.getClose();
//                double change = ltp - close;
//                double percentChange = (change / close) * 100;
//
//                indexDataList.add(createIndexEntry(
//                        indexName,
//                        ltp,
//                        change,
//                        percentChange
//                ));
//            } else {
//                logger.warn("Market data not found for key: " + key + ", using random value as fallback");
//                double fallbackValue = getFallbackValue(indexName);
//                // For fallback, we'll use default values for change/percentChange
//                indexDataList.add(createIndexEntry(
//                        indexName,
//                        fallbackValue,
//                        10,
//                        1.23
//                ));
//            }
//        }
//
//        return indexDataList;
//    }
//
//    private Map<String, Object> createIndexEntry(String name, double value, double change, double percentChange) {
//        Map<String, Object> entry = new HashMap<>();
//        entry.put("name", name);
//        entry.put("value", Math.round(value * 100.0) / 100.0);
//        entry.put("change", Math.round(change * 100.0) / 100.0);
//        entry.put("percentChange", Math.round(percentChange * 100.0) / 100.0);
//        return entry;
//    }
//
//
//    private double getFallbackValue(String indexName) {
//        switch(indexName) {
//            case "Nifty 50":
//                return 0;
//            case "Nifty Bank":
//                return 0;
//            case "Sensex":
//                return 0;
//            default:
//                return 0.0;
//        }
//    }
//}
