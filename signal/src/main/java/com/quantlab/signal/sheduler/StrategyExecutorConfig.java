package com.quantlab.signal.sheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class StrategyExecutorConfig {
    @Bean
    public Executor strategyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(30); // or 2x vCPUs
        executor.setMaxPoolSize(60);
        executor.setQueueCapacity(10000);
        executor.setThreadNamePrefix("strategy-exec-");
        executor.initialize();
        return executor;
    }
}
