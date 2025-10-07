package com.quantlab.signal.sheduler;
import com.quantlab.common.emailService.EmailService;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.redisService.TouchLineRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalTime;

@Component
public class MarketDataUnchangedScheduler {

    private static final Logger logger = LogManager.getLogger(MarketDataUnchangedScheduler.class);

    private final TouchLineRepository touchLineRepository;
    private final EmailService emailService;

    private Double lastSeenLTP = null;
    private Long lastSeenLut = null;

    @Autowired
    public MarketDataUnchangedScheduler(TouchLineRepository touchLineRepository, EmailService emailService) {
        this.touchLineRepository = touchLineRepository;
        this.emailService = emailService;
    }

    @Autowired
    BodSchedule bodSchedule;

    @Scheduled(fixedRate = 60000)
    public void monitorTouchline() {

        LocalTime now = LocalTime.now();
        LocalTime startTime = LocalTime.of(9, 16);
        LocalTime endTime = LocalTime.of(15, 30);

        if (!bodSchedule.shouldRunScheduler()) {
            return;
        }

        if (now.isBefore(startTime) || now.isAfter(endTime)) {
            logger.debug("Outside monitoring hours, skipping execution.");
            return;
        }

        MarketData currentData = getNiftyMarketData();
        if (currentData == null) {
            emailService.sendEmailAlertForNullData("MD_26000");
            return;
        }

        logger.debug("NIFTY Touchline LTP: {}, LastTradedTime: {}", currentData.getLTP(), currentData.getLut());

        if (lastSeenLTP == null || lastSeenLut == null) {
            lastSeenLTP = currentData.getLTP();
            lastSeenLut = currentData.getLut();
            logger.debug("Initialized last seen LTP and traded time.");
            return;
        }

        if (currentData.getLTP() == lastSeenLTP && currentData.getLut() == lastSeenLut) {
            emailService.sendEmailAlert(currentData.getLTP(), currentData.getLut());
        } else {
            lastSeenLTP = currentData.getLTP();
            lastSeenLut = currentData.getLut();
        }
    }

    private MarketData getNiftyMarketData() {
        try {
            MarketData data = touchLineRepository.find("MD_26000");
            if (data == null) throw new Exception("MarketData not found");
            return data;
        } catch (Exception e) {
            logger.error("Error fetching NIFTY MarketData", e);
            return null;
        }
    }

}



