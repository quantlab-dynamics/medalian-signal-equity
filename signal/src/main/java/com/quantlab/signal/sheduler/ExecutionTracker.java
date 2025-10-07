package com.quantlab.signal.sheduler;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExecutionTracker {

        private final Set<Long> runningStrategyIds = ConcurrentHashMap.newKeySet();

        public boolean tryLock(Long strategyId) {
            return runningStrategyIds.add(strategyId); // returns false if already being processed
        }

        public void releaseLock(Long strategyId) {
            runningStrategyIds.remove(strategyId);
        }

        public void clearAll() {
            runningStrategyIds.clear(); // optional if needed between cycles
        }
    }


