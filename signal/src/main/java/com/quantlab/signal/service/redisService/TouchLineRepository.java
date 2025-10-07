package com.quantlab.signal.service.redisService;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.grpcserver.PositionStreamGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.MASTERDATA;

@Repository
    public class TouchLineRepository {
    private static final Logger logger = LoggerFactory.getLogger(TouchLineRepository.class);


    @Autowired
    @Qualifier("redisTemplate1")
    private final RedisTemplate<String, MarketData> redisTemplate;

    @Autowired
    @Qualifier("redisTemplateList")
    private final RedisTemplate<String, String> redisExpiryDatesTemplate;

    @Autowired
    @Qualifier("redisTemplate5")
    private final RedisTemplate<String, MasterResponseFO> redisMasterResponseFOTemplate;


    public TouchLineRepository(RedisTemplate<String, MarketData> redisTemplate,
                               @Qualifier("redisTemplateList") RedisTemplate<String, String> redisExpiryDatesTemplate,
                               @Qualifier("redisTemplate5") RedisTemplate<String, MasterResponseFO> redisMasterResponseFOTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisMasterResponseFOTemplate = redisMasterResponseFOTemplate;
        this.redisExpiryDatesTemplate = redisExpiryDatesTemplate;
    }

    public void save(String key, MarketData touchLine) {
        try {
            redisTemplate.opsForValue().set(key, touchLine);
        } catch (Exception e) {
            // Log the exception details
            logger.error("Error setting value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }

    }

    public MarketData find(String key) {
        MarketData touchlineBinaryResposne = null;
        try {
            touchlineBinaryResposne = redisTemplate.opsForValue().get(key);
        }
        catch (Exception e) {
            // Log the exception details
            logger.error("Error finding value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
        return touchlineBinaryResposne;
    }

    public Map<String, MarketData> findMultiple(List<String> keys) {
        try {
            List<MarketData> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null || values.size() != keys.size()) {
                return Collections.emptyMap();
            }

            Map<String, MarketData> result = new HashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                if (values.get(i) != null) {
                    result.put(keys.get(i), values.get(i));
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Error finding multiple values in Redis", e);
            return Collections.emptyMap();
        }
    }

    public MasterResponseFO findMaster(String key) {
        MasterResponseFO touchlineBinaryResposne = null;
        try {
            touchlineBinaryResposne = redisMasterResponseFOTemplate.opsForValue().get(key);
        }
        catch (Exception e) {
            // Log the exception details
            logger.error("Error finding value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
        return touchlineBinaryResposne;
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            // Log the exception details
            logger.error("Error deleting value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
    }

    public List<String> fetchExpiryDates(String listKey) {
        List<String> redisData = null;
        try {
            redisData = redisExpiryDatesTemplate.opsForList().range(listKey, 0, -1);
        }
        catch (Exception e) {
            // Log the exception details
            logger.error("Error getting value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
        return redisData;
    }

    public List<MasterResponseFO> fetchMasterResponseFO(String instrumentExpiryDateKey) {
        List<MasterResponseFO> redisData = null;
        try {
            redisData = redisMasterResponseFOTemplate.opsForList().range(instrumentExpiryDateKey, 0, -1);
        }
        catch (Exception e) {
            // Log the exception details
            logger.error("Error getting value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
        return redisData;
    }

    public String fetchTokens(String key) {
        String value = null;
        try {
            value = redisExpiryDatesTemplate.opsForValue().get(key);
        }
        catch (Exception e) {
            // Log the exception details
            logger.error("Error finding tokens in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
        return value;
    }

    public String saveTokens(String key, String value) {
        try {
            redisExpiryDatesTemplate.opsForValue().set(key, value, Duration.ofSeconds(900));
        }
        catch (Exception e) {
            // Log the exception details
            logger.error("Error saving tokens in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
        return value;
    }


}



//}
//public void saveList(String key, TouchlineBinaryResposne touchLine) {
//            redisTemplate.opsForValue().set(key, touchLine);
//
//        }
//
//        public List<TouchlineBinaryResposne> findAll(String key) {
//            ListOperations<String, TouchlineBinaryResposne> listOps = redisTemplate.opsForList();
//            return listOps.range(key, 0, -1);
//        }
//
//        public void clearList(String key) {
//            redisTemplate.delete(key);
//        }
//    }
