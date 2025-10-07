package com.quantlab.signal.sheduler;

import com.quantlab.signal.utils.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderScheduler {

    private static final Logger logger = LogManager.getLogger(OrderScheduler.class);

    @Autowired
    private Schedule schedule;

    @Autowired
    private CommonUtils commonUtils;

    //    @Scheduled(fixedRate = 1000)
    public void GenerateSchedule() {
        if (LocalTime.now().isAfter(LocalTime.of(9, 15)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
            CompletableFuture.runAsync(() -> {
                if (commonUtils.shouldRunScheduler()) {
                    if (LocalTime.now().isAfter(LocalTime.of(9, 15)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
                        schedule.scheduleTask();
                    }
                }
            });
        }
    }

}
