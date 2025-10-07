package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.strategy.SignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
    public class TouchLineService {


    private  TouchLineRepository repository;

    private static final Logger logger = LoggerFactory.getLogger(TouchLineService.class);


    public TouchLineService(TouchLineRepository touchLineRepository) {
        this.repository = touchLineRepository;
    }

    public void saveTouchLine(String key, MarketData touchLine) {
        repository.save(key, touchLine);
    }

    public MarketData getTouchLine(String key) {
                MarketData marketData = null;
                try {
                    marketData = repository.find("MD_" +key);
                    if (marketData == null)
                        throw new Exception("MarketData is not found for key: MD_"+key);
                } catch (Exception e) {
                    logger.error("no MasterData found for key : {}", key);
                }

        return marketData;
    }

    public Map<String, MarketData> getMultipleTouchLines(List<String> keys) {
        try {
            List<String> prefixedKeys = keys.stream()
                    .map(key -> "MD_" + key)
                    .collect(Collectors.toList());

            return repository.findMultiple(prefixedKeys);
        } catch (Exception e) {
            logger.error("Error fetching multiple MarketData entries", e);
            return Collections.emptyMap();
        }
    }

    public MasterResponseFO getMaster_(String key) {
        return repository.findMaster("MASTER_" +key);
    }

    public String getToken(String key) {
        return repository.fetchTokens("TOKEN_" +key);
    }


    public String saveToken(String key, String value) {
        return repository.saveTokens("TOKEN_" +key, value);
    }

    public List<String> getExpiryDates(String key) {
        return repository.fetchExpiryDates(key);
    }

    public List<MasterResponseFO> getMasterResponseFO(String instrumentExpiryDateKey) {
        return repository.fetchMasterResponseFO(instrumentExpiryDateKey);
    }

//    public Boolean keyExists(String key) {
//        return repository.keyExists(key);
//    }

    public void deleteTouchLine(String key) {
        repository.delete(key);
    }
}


//        public void saveTouchLine(String key, TouchlineBinaryResposne touchLine) {
//            repository.saveList(key, touchLine);
//        }
//
//        public List<TouchlineBinaryResposne> getTouchLine(String key) {
//            return repository.findAll(key);
//        }
//
//        public void deleteTouchLine(String key) {
//            repository.clearList(key);
//        }
//    }

