package com.quantlab.signal.strategy.aspects;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyStatus;
import com.quantlab.signal.service.ErrorManagementService;
import com.quantlab.signal.strategy.SignalService;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RetryAspect {

    private static final Logger logger = LoggerFactory.getLogger(RetryAspect.class);

    @Autowired
    private ErrorManagementService errorManagementService;

    @After("execution(* com.quantlab.signal.strategy.StrategiesImplementation.check(..)) && args(strategy)")
    public void RetryStrategy(Strategy strategy) {
        if (!strategy.getStatus().equalsIgnoreCase(StrategyStatus.RETRY.getKey())){
            return;
        }else {
            logger.info("Retry strategy started for the strategy ID : {}", strategy.getId());
            errorManagementService.retrySignal(strategy.getId());
        }
    }
}
