package com.quantlab.signal.service.redisService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.redisDto.StrategyCheckEvent;
import com.quantlab.signal.sheduler.StrategyScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class StrategyCheckEventConsumer {

    private final StrategyScheduler strategyScheduler;
    private final StrategyRepository strategyRepository;
    private final ObjectMapper objectMapper;

    public StrategyCheckEventConsumer(StrategyScheduler strategyScheduler,
                                      StrategyRepository strategyRepository,
                                      ObjectMapper objectMapper) {
        this.strategyScheduler = strategyScheduler;
        this.strategyRepository = strategyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles incoming Redis messages
     */
    public void handleMessage(String message) {
        try {
            log.info("Received Redis message: {}", message);

            // Parse the JSON message
            StrategyCheckEvent event = objectMapper.readValue(message, StrategyCheckEvent.class);
            processEvent(event);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse strategy check event JSON: {}", message, e);
        } catch (Exception e) {
            log.error("Error processing strategy check event", e);
        }
    }

    /**
     * Alternative method if using GenericJackson2JsonRedisSerializer
     */
    public void handleMessage(StrategyCheckEvent event) {
        try {
            log.info("Received strategy check event for strategyId: {}", event.getStrategyId());
            processEvent(event);
        } catch (Exception e) {
            log.error("Error processing strategy check event", e);
        }
    }

    private void processEvent(StrategyCheckEvent event) {
        Long strategyId = event.getStrategyId();

        // Validate the event
        if (strategyId == null) {
            log.info("Received event with null strategyId");
            return;
        }

        log.info("Processing strategy check event for strategyId: {}", strategyId);

        // Option 1: Pass only ID to scheduler (let scheduler fetch strategy)
//        strategyScheduler.checkStrategyAsync(strategyId);

        // Option 2: Fetch strategy here and pass the object
         fetchAndProcessStrategy(strategyId);
    }

    /**
     * Alternative approach: Fetch strategy details before scheduling
     */
    private void fetchAndProcessStrategy(Long strategyId) {
        try {
            Optional<Strategy> strategyOpt = strategyRepository.findById(strategyId);
            if (strategyOpt.isPresent()) {
                Strategy strategy = strategyOpt.get();

                // Only process if strategy is still active
                if (Status.ACTIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                    strategyScheduler.checkStrategyAsync(strategy);
                    log.info("Successfully scheduled strategy check for strategyId: {}", strategyId);
                } else {
                    log.info("Strategy {} is no longer active, skipping check", strategyId);
                }
            } else {
                log.warn("Strategy not found for id: {}", strategyId);
            }
        } catch (Exception e) {
            log.error("Error fetching strategy for id: {}", strategyId, e);
        }
    }
}
