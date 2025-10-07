package com.quantlab.signal.sheduler;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.signal.grpcserver.OrderPlaceGrpc;
import com.quantlab.signal.strategy.driver.Parser;
import com.quantlab.signal.utils.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.quantlab.common.utils.staticstore.AppConstants.FETCH_STRATEGIES_BY_STATUS;

@Service
public class StrategyScheduler {

    private static final Logger logger = LogManager.getLogger(StrategyScheduler.class);

    @Autowired
    private final StrategyRepository strategyRepository;

    @Autowired
    private final CommonUtils commonUtils;

    private final Parser parser;

    // Cached strategies snapshot
    private volatile List<Strategy> strategyCache = List.of();

    // Running flag to avoid overlapping runs
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Custom executor for strategy tasks
    private final ExecutorService taskExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public StrategyScheduler(StrategyRepository strategyRepository, Parser parser, CommonUtils commonUtils) {
        this.strategyRepository = strategyRepository;
        this.parser = parser;
        this.commonUtils = commonUtils;
    }

    @Scheduled(fixedRate = 5000)
    public void refreshStrategies() {
        if (isMarketOpen()) {
            List<Strategy> strategies = strategyRepository.findStrategies("N", "Y", FETCH_STRATEGIES_BY_STATUS);
            strategyCache = List.copyOf(strategies); // immutable snapshot
        }
    }

    @Scheduled(fixedRate = 1000)
    public void generateSchedule() {
        if (isMarketOpen() && commonUtils.shouldRunScheduler()) {
            if (!running.get()) {
                CompletableFuture.runAsync(this::scheduleTask, taskExecutor);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleTask() {
        if (!running.compareAndSet(false, true)) {
            return; // skips if already running
        }

        Instant startTime = Instant.now();
        try {
            List<Strategy> strategies = strategyCache;

            List<CompletableFuture<Void>> futures = strategies.stream()
                    .map(this::checkStrategyAsync)
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            running.set(false);
        }

        long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        logger.info("Schedule task completed in {} ms", duration);
    }

    public CompletableFuture<Void> checkStrategyAsync(Strategy strategy) {
        return CompletableFuture.runAsync(() -> parser.check(strategy), taskExecutor);
    }


    private boolean isMarketOpen() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 15)) && now.isBefore(LocalTime.of(15, 30));
    }
}
