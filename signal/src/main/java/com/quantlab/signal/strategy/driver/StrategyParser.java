package com.quantlab.signal.strategy.driver;

import com.quantlab.common.entity.Strategy;
import com.quantlab.signal.strategy.StrategiesImplementation;
import com.quantlab.signal.utils.StrategyMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StrategyParser implements  Parser {

    Map<String,String> StrategyMap = new HashMap<String,String>();

    @Autowired
    private ApplicationContext applicationContext;

    private final ConcurrentHashMap<Long, Object> strategyLocks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Arrays.stream(StrategyMapper.values())
                .forEach(type -> StrategyMap.put(type.getKey(), type.getLabel()));

    }

    @Override
    public void execute(Strategy strategy) {
        try {
            String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
            StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
            strategiesImplementation.check(strategy);

        }catch (Exception e) {
//      e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }



    @Override
    public void runStrategy(Strategy strategy) {
        try {

            String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
            StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
            strategiesImplementation.runStrategy(strategy);

        }catch (Exception e) {
//      e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exit(Strategy strategy) {
        try {

            String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
            StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
            strategiesImplementation.exitStrategy(strategy);

        }catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void check(Strategy strategy) {
        Object lock = strategyLocks.computeIfAbsent(strategy.getId(), k -> new Object());
        synchronized (lock) {
            try {
                String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
                StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
                strategiesImplementation.check(strategy);
            } finally {
                strategyLocks.remove(strategy.getId());
            }
        }
    }

}
