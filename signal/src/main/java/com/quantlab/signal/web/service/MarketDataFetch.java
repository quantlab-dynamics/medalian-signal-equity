package com.quantlab.signal.web.service;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.common.utils.staticstore.dropdownutils.StrikeSelectionMenu;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.MasterRepository;
import com.quantlab.signal.service.redisService.SyntheticPriceRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.quantlab.signal.utils.staticdata.StaticStore.redisIndexStrikePrices;

@Component
public class MarketDataFetch {

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    private MasterRepository masterRepository;


    @Autowired
    SyntheticPriceRepository syntheticPriceRepository;


    private static final Logger logger = LoggerFactory.getLogger(MarketDataFetch.class);

    public MarketLiveDto getMarketData(String underling  , String expiry) {

        MarketLiveDto marketLiveDto = new MarketLiveDto();
        IndexInstruments instrument  = IndexInstruments.fromKey(underling);
        MarketData marketData = touchLineService.getTouchLine(String.valueOf(instrument.getLabel()));
        marketLiveDto.setName(underling);
        marketLiveDto.setSpotPrice(marketData.getLTP());
        Integer atm = getATM(underling,marketData.getLTP() , commonUtils.getExpiryShotDateByIndex(expiry, underling, OptionType.FUTURE.getKey()));
        marketLiveDto.setAtm(atm);
        return  marketLiveDto;
    }

    public MarketLiveDto getMarketData(Strategy strategy) {
        String atmType = strategy.getAtmType();
        String underlying = strategy.getUnderlying().getName();

        MarketLiveDto marketLiveDto = new MarketLiveDto();

        IndexInstruments instrument = IndexInstruments.fromKey(underlying);

        MarketData marketData = touchLineService.getTouchLine(String.valueOf(instrument.getLabel()));

        marketLiveDto.setName(underlying);
        marketLiveDto.setSpotPrice(marketData.getLTP());

        Integer atm;
        if (StrikeSelectionMenu.FUTURE_ATM.getKey().equalsIgnoreCase(atmType)) {
            String futureKey = underlying.toUpperCase(Locale.ROOT) +
                    commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.FUTURE.getKey()) +
                    OptionType.FUTURE.getKey();
            MarketData futureData = touchLineService.getTouchLine(String.valueOf(getMasterResponse(futureKey).getExchangeInstrumentID()));
            atm = getATM(underlying, futureData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.FUTURE.getKey()) );
        } else if (StrikeSelectionMenu.SYNTHETIC_ATM.getKey().equalsIgnoreCase(atmType)) {
            atm = getATM(underlying,syntheticPriceRepository.find(underlying).getPrice(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.FUTURE.getKey()) );
        } else {
            atm = getATM(underlying, marketData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.FUTURE.getKey()) );
        }
        marketLiveDto.setSyntheticAtm(getATM(underlying, syntheticPriceRepository.find(underlying).getPrice(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey())));
        marketLiveDto.setSyntheticPrice(syntheticPriceRepository.find(underlying).getPrice());



        marketLiveDto.setAtm(atm);
        return marketLiveDto;
    }

    public Integer getATM(String underlying, double price , String expiry) {
        List<Integer> strikeList = redisIndexStrikePrices.get(underlying+"_"+expiry);


        return findClosestStrike(strikeList, price);
    }


    public int findClosestStrike(List<Integer> strikeList, double ltp) {
        int left = 0, right = strikeList.size() - 1;

        if (ltp <= strikeList.get(0)) return strikeList.get(0);
        if (ltp >= strikeList.get(right)) return strikeList.get(right);

        // Binary Search to find the closest strikes
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (strikeList.get(mid) == ltp) {
                return strikeList.get(mid);
            } else if (strikeList.get(mid) < ltp) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        // left is now the index of the closest higher value
        // right is the index of the closest lower value
        int lower = strikeList.get(right);
        int higher = strikeList.get(left);

        // Return the nearest value
        return (ltp - lower <= higher - ltp) ? lower : higher;
    }

    public MasterResponseFO getMasterResponse(String key) {
        try {
            MasterResponseFO responseFO = masterRepository.find(key);
            if (responseFO != null)
                return responseFO;
            throw new RuntimeException("no Master data found for key = "+key);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error fetching master data for key: " +key );
            logger.error(e.getMessage());
        }
        return null;
    }

    public MarketData getInstrumentData(Long instrumentId) {

        if (instrumentId != null) {
            return touchLineService.getTouchLine(instrumentId.toString());
        }
        return null;
    }

    public Map<String, List<String>> getInstrumentExpiryDates(ArrayList<String> expiryInstrumentIds) {
        Map<String, List<String>> redisData = new HashMap<>();
        for (String instrumentId : expiryInstrumentIds) {
            List<String> dates = touchLineService.getExpiryDates(instrumentId);
            redisData.put(instrumentId,dates);
        }
        return redisData;
    }

    public List<MasterResponseFO> getMasterResponseFO(String instrumentExpiryDateKey) {
        return touchLineService.getMasterResponseFO(instrumentExpiryDateKey);
    }

    public Double getSyntheticPrice (Strategy strategy,String underlying) {
        return syntheticPriceRepository.find(underlying).getPrice();
    }
}
