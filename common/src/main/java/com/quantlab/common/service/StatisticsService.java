package com.quantlab.common.service;

import com.quantlab.common.dto.StatisticsResponseDto;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.exception.custom.StrategyNotFoundException;
import com.quantlab.common.repository.AppUserRepository;
import com.quantlab.common.repository.StrategyRepository;

import com.quantlab.common.utils.StatisticsUtils;
import com.quantlab.common.utils.staticstore.dropdownutils.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);


    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private AppUserRepository appUserRepository;


    @Transactional(readOnly = true)
    public StatisticsResponseDto getStatistics(Long strategyId) {
        log.info("Fetching statistics for strategyId: {}", strategyId);

        try{
        Strategy strategy = strategyRepository.findById(strategyId).orElseThrow(() -> new StrategyNotFoundException("Strategy not found for signalId: " + strategyId));

        // Create a sample response with default values
            StatisticsResponseDto statistics = StatisticsUtils.calculateStatistics(strategy);

        return statistics;
        }catch (Exception e) {
            log.error("Error fetching statistics for strategyId: {}", strategyId, e);
            throw new RuntimeException("Failed to fetch statistics");
        }
    }


    public Strategy findProcessingStrategy(Long strategyId) {
        Optional<Strategy> strategyOpt = strategyRepository.findById(strategyId);
        if (strategyOpt.isEmpty()) {
            log.warn("Strategy with ID {} not found", strategyId);
            throw new StrategyNotFoundException("Strategy not found for ID: " + strategyId);
        }
        Strategy strategy = strategyOpt.get();
        if (!SubscriptionStatus.START.getKey().equalsIgnoreCase(strategy.getSubscription()) && strategy.getSourceId() != null) {
            log.warn("Strategy with ID {} is not subscribed or and has sourceId", strategy.getId());
            AppUser firstUser = findFirstUser();
            Optional<Strategy> sourceStrategyOpt = strategyRepository.findByNameAndAppUser(strategy.getName(),firstUser);
            if (sourceStrategyOpt.isPresent()) {
                strategy = sourceStrategyOpt.get();
            } else {
                log.warn("No source strategy found for strategyId: {}", strategy.getSourceId());
            }
        }
        return strategy;
    }

    private AppUser findFirstUser() {

        return appUserRepository.findByUserRole_id(4L)
                .orElseThrow(() -> {
                    log.warn("No user found with role ID 4");
                    return new RuntimeException("No data found");
                });
    }

}
