package com.quantlab.signal.utils;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.common.utils.staticstore.dropdownutils.Segment;
import com.quantlab.common.utils.staticstore.dropdownutils.SegmentType;
import com.quantlab.signal.dto.HedgeData;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.MasterRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.el.lang.ELSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.StrategyAppConfigData.INDEX_STRIKE_DIFFERENCE;

@Component
@Slf4j
public class StrategyUtils {
    @Autowired
    MasterRepository masterRepository;

    @Autowired
    @Qualifier("redisTemplate5")
    RedisTemplate<String, MasterResponseFO> redisTemplate;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    TouchLineService touchLineService;

    @PostConstruct
    public void init() {
        INDEX_STRIKE_DIFFERENCE.put("NIFTY", 100);
        INDEX_STRIKE_DIFFERENCE.put("BANKNIFTY", 200);
    }


    public StrategyLeg SignalStrategyLegMapper(Strategy strategy){
        StrategyLeg strategyLeg = new StrategyLeg();
        return strategyLeg;
    }

    public Signal StrategySignalMapper (Strategy strategy){
        Signal signal = new Signal();
        signal.setStrategy(strategy);
        signal.setCapital(strategy.getMinCapital());
        return signal;
    }

    public HedgeData getHedgeData(double ltp, int percent, String instrumentType, int ATM, String underlying, Strategy strategy, String condition) {
        double required = (ltp * percent) / 100;
        return getHedgeDataCommon(required, ATM, instrumentType, strategy, condition, "LTP");
    }

    public HedgeData getHedgeDataByDelta(double targetDelta, String instrumentType, int ATM, String underlying, Strategy strategy, String condition) {
        return getHedgeDataCommon(targetDelta, ATM, instrumentType, strategy, condition, "DELTA");
    }

    public HedgeData getHedgeDataCommon(double targetValue, int ATM, String instrumentType, Strategy strategy, String condition, String type) {
        // Build Redis key pattern
        String underlyingName = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
        String expiryStr = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlyingName, OptionType.OPTION.getKey());
        String optionSuffix = instrumentType.equalsIgnoreCase(SegmentType.CE.getKey()) ? "CE" : "PE";
        String keyPattern = underlyingName + expiryStr + "*" + optionSuffix;
        System.out.println("Key pattern: " + keyPattern);

        double lowerLimit = ATM * 0.8;
        double upperLimit = ATM * 1.2;

        Set<String> masterKeys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(keyPattern).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // Extract strike price from key
                try {
                    String[] parts = key.split("-");
                    if (parts.length >= 2) {
                        String strikeWithSuffix = parts[parts.length - 1];
                        String strikeStr = strikeWithSuffix.replaceAll("[^0-9]", "");
                        if (!strikeStr.isEmpty()) {
                            int strike = Integer.parseInt(strikeStr);
                            if (strike >= lowerLimit && strike <= upperLimit) {
                                masterKeys.add(key);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Invalid key format: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Error while scanning Redis keys with pattern: {}", keyPattern, e);
            return null;
        }

        if (masterKeys.isEmpty()) {
            log.warn("No keys found in 20% range for pattern: {}", keyPattern);
            return null;
        }


        Map<Integer, String> keyMap = new ConcurrentHashMap<>();
        List<MasterResponseFO> masterList = masterKeys.parallelStream()
                .map(key -> {
                    MasterResponseFO master = marketDataFetch.getMasterResponse(key);
                    if (master != null) {
                        keyMap.put(master.getExchangeInstrumentID(), key);
                    }
                    return master;
                })
                .filter(Objects::nonNull)
                .toList();

        List<MarketData> marketDataList = masterList.parallelStream()
                .map(master -> touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID())))
                .filter(Objects::nonNull)
                .toList();

        MarketData bestCandidate = switch (condition.toUpperCase()) {
            case "LESS_THAN" -> marketDataList.stream()
                    .filter(md -> (type.equals("LTP") ? md.getLTP() : md.getDelta()) <= targetValue)
                    .min(Comparator.comparingDouble(md -> targetValue - (type.equals("LTP") ? md.getLTP() : md.getDelta())))
                    .orElse(null);
            case "GREATER_THAN" -> marketDataList.stream()
                    .filter(md -> (type.equals("LTP") ? md.getLTP() : md.getDelta()) >= targetValue)
                    .min(Comparator.comparingDouble(md -> (type.equals("LTP") ? md.getLTP() : md.getDelta()) - targetValue))
                    .orElse(null);
            case "NEAREST" -> marketDataList.stream()
                    .min(Comparator.comparingDouble(md -> Math.abs((type.equals("LTP") ? md.getLTP() : md.getDelta()) - targetValue)))
                    .orElse(null);
            default -> throw new IllegalArgumentException("Invalid condition: " + condition);
        };

        if (bestCandidate != null) {
            HedgeData hedgeData = new HedgeData();
            hedgeData.setLiveMarketData(bestCandidate);

            MasterResponseFO bestMaster = masterList.stream()
                    .filter(m -> m.getExchangeInstrumentID() == bestCandidate.getExchangeInstrumentId())
                    .findFirst()
                    .orElse(null);
            hedgeData.setMasterData(bestMaster);

            String finalKey = keyMap.get(bestCandidate.getExchangeInstrumentId());
            hedgeData.setFinalKey(finalKey);

            return hedgeData;
        }

        log.warn("No suitable candidate found for target {}: {} and condition: {}", type, targetValue, condition);
        return null;
    }


    public String getSegment(String underlyingName) {
        if (underlyingName.contains("NIFTY")) {
            return Segment.NSE.getKey();
        }else {
            return Segment.BSE.getKey();
        }
    }


//    public HedgeData getHedgeData(double ltp, int percent, String instrumentType, int ATM, String underlying, Strategy strategy, String condition) {
//        double required = (ltp * percent) / 100;
//
//        // Build Redis key pattern
//        String underlyingName = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
//        String expiryStr = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlyingName);
//        String optionSuffix = instrumentType.equalsIgnoreCase(LEG_TYPE_CALL) ? "CE" : "PE";
//        String keyPattern = underlyingName + expiryStr + "*" + optionSuffix;
//
//
//        Set<String> masterKeys = new HashSet<>();
//        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(keyPattern).build())) {
//            while (cursor.hasNext()) {
//                masterKeys.add(cursor.next());
//            }
//        } catch (Exception e) {
//            log.error("Error while scanning Redis keys with pattern: {}", keyPattern, e);
//            return null;
//        }
//
//        if (masterKeys.isEmpty()) {
//            log.warn("No keys found for pattern: {}", keyPattern);
//            return null;
//        }
//
//        // Fetch master data in parallel
//        List<MasterResponseFO> masterList = masterKeys.parallelStream()
//                .map(key -> marketDataFetch.getMasterResponse(key))
//                .filter(Objects::nonNull)
//                .toList();
//
//        // Fetch market data in parallel
//        List<MarketData> marketDataList = masterList.parallelStream()
//                .map(master -> touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID())))
//                .filter(Objects::nonNull)
//                .toList();
//
//        // Find the best candidate based on the condition
//        MarketData bestCandidate = switch (condition.toUpperCase()) {
//            case "LESS_THAN" -> marketDataList.stream()
//                    .filter(md -> md.getLTP() <= required)
//                    .min(Comparator.comparingDouble(md -> required - md.getLTP()))
//                    .orElse(null);
//            case "GREATER_THAN" -> marketDataList.stream()
//                    .filter(md -> md.getLTP() >= required)
//                    .min(Comparator.comparingDouble(md -> md.getLTP() - required))
//                    .orElse(null);
//            case "NEAREST" -> marketDataList.stream()
//                    .min(Comparator.comparingDouble(md -> Math.abs(md.getLTP() - required)))
//                    .orElse(null);
//            default -> throw new IllegalArgumentException("Invalid condition: " + condition);
//        };
//
//        if (bestCandidate != null) {
//            HedgeData hedgeData = new HedgeData();
//            hedgeData.setLiveMarketData(bestCandidate);
//
//            // Retrieve the corresponding master data
//            MasterResponseFO bestMaster = masterList.stream()
//                    .filter(m -> m.getExchangeInstrumentID() == bestCandidate.getExchangeInstrumentId())
//                    .findFirst()
//                    .orElse(null);
//            hedgeData.setMasterData(bestMaster);
//
//            return hedgeData;
//        }
//
//        log.warn("No suitable candidate found for required LTP: {} and condition: {}", required, condition);
//        return null;
//    }

//    public HedgeData getHedgeData(double ltp, int percent, String instrumentType, int ATM, String underling, Strategy strategy){
//        double required = (ltp *percent) /100;
//        double newLtp=ltp;
//        int count =1;
//        int multiplier = INDEX_STRIKE_DIFFERENCE.get(underling.toUpperCase());
//        // has to change the  nifty dynamically
//        if (instrumentType.equalsIgnoreCase(LEG_TYPE_CALL)){
//            while (required <= newLtp){
//                // has to change for the difference level
//                int newAtm = count*multiplier+ATM;
//                MasterResponseFO master = marketDataFetch.getMasterResponse(strategy.getUnderlying().getName().toUpperCase(Locale.ROOT)+commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName())+newAtm+"CE");
//                MarketData touchLine = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
//                if (Math.round(touchLine.getLTP())<=Math.round(required)){
//                    HedgeData hedgeData = new HedgeData();
//                    hedgeData.setLiveMarketData(touchLine);
//                    hedgeData.setMasterData(master);
//                    return hedgeData;
//                }
//                count++;
//                newLtp = touchLine.getLTP();
//            }
//        }else if (instrumentType.equalsIgnoreCase(LEG_TYPE_PUT) ){
//            while (required <= newLtp){
//                int newAtm =ATM-count*multiplier;
//                MasterResponseFO master = marketDataFetch.getMasterResponse(strategy.getUnderlying().getName().toUpperCase(Locale.ROOT)+commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName())+newAtm+"PE");
//                MarketData touchLine = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
//                if (Math.round(touchLine.getLTP())<=Math.round(required)){
//                    HedgeData hedgeData = new HedgeData();
//                    hedgeData.setLiveMarketData(touchLine);
//                    hedgeData.setMasterData(master);
//                    return hedgeData;
//                }
//                count++;
//                newLtp = touchLine.getLTP();
//            }
//        }
//    return null;
//    }
}
