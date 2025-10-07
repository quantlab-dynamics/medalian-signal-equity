package com.quantlab.signal.service.redisService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StrategyPnlCacheService {

    @Autowired
    @Qualifier("pnlRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STRATEGY_PNL_KEY = "strategy:PNL";

    public StrategyPnlCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveStrategyPnl(Long strategyId, Double pnl) {
        redisTemplate.opsForHash().put(STRATEGY_PNL_KEY, strategyId.toString(), pnl);
    }

    public Double getStrategyPnl(Long strategyId) {
        Object value = redisTemplate.opsForHash().get(STRATEGY_PNL_KEY, strategyId.toString());
        return value != null ? Double.valueOf(value.toString()) : null;
    }

    public Map<Object, Object> getAllPnls() {
        return redisTemplate.opsForHash().entries(STRATEGY_PNL_KEY);
    }
}
