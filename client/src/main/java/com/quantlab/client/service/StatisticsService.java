//package com.quantlab.client.service;
//
//import com.quantlab.client.dto.StatisticsResponseDto;
//import com.quantlab.client.dto.StatisticsResponseObjDto;
//import com.quantlab.client.utils.StatisticsUtils;
//import com.quantlab.common.entity.Signal;
//import com.quantlab.common.entity.Strategy;
//import com.quantlab.common.exception.custom.SignalNotFoundException;
//import com.quantlab.common.exception.custom.StrategyNotFoundException;
//import com.quantlab.common.repository.SignalRepository;
//import com.quantlab.common.repository.StrategyRepository;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Service
//public class StatisticsService {
//
//    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
//
//    @Autowired
//    private StrategyRepository strategyRepository;
//
//    @Transactional(readOnly = true)
//    public StatisticsResponseDto getStatistics(Long strategyId) {
//        log.info("Fetching statistics for strategyId: {}", strategyId);
//
//        try{
//        Strategy strategy = strategyRepository.findById(strategyId).orElseThrow(() -> new StrategyNotFoundException("Strategy not found for signalId: " + strategyId));
//
//        // Create a sample response with default values
//            StatisticsResponseDto statistics = StatisticsUtils.calculateStatistics(strategy);
//
//        return statistics;
//        }catch (Exception e) {
//            log.error("Error fetching statistics for strategyId: {}", strategyId, e);
//            throw new RuntimeException("Failed to fetch statistics");
//        }
//    }
//}
