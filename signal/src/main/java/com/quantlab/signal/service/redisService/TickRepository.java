package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.MarketData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TickRepository {

    @Autowired
    @Qualifier("redisTemplate1")
    private RedisTemplate<String, MarketData> redisTemplate;

    public TickRepository(@Qualifier("redisTemplate1") RedisTemplate<String, MarketData> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveList(String key, List<MarketData> marketDepth){
        ListOperations<String, MarketData> listOps = redisTemplate.opsForList();
        listOps.rightPushAll("TICK_"+key, marketDepth);
    }


    public List<MarketData> findAll(String key){
        ListOperations<String, MarketData> listOps = redisTemplate.opsForList();
        return listOps.range("TICK_"+key, 0, -1);
    }
    public boolean keyExists(String key) {
        return redisTemplate.hasKey("TICK_"+key);
    }

    public void clearList(String key){
        redisTemplate.delete("TICK_"+key);
    }

    public void save(String key, MarketData marketDepth) {

        redisTemplate.opsForValue().set("TICK_"+key, marketDepth);
    }

    public MarketData find(String key) {
        return redisTemplate.opsForValue().get(key);
    }


}
