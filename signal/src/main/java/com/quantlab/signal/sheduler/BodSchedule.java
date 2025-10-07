package com.quantlab.signal.sheduler;

import com.quantlab.common.emailService.EmailService;
import com.quantlab.common.entity.EntryDays;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.repository.UserAuthConstantsRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.signal.dto.ExpiryDatesDTO;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.HolidayService;
import com.quantlab.signal.service.redisService.InterActiveTokensRepository;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.common.utils.staticstore.AppConstants.BOD_EMAILS;
import static com.quantlab.signal.utils.staticdata.StaticStore.*;

@Component
public class BodSchedule {

    private static final Logger logger = LogManager.getLogger(BodSchedule.class);


    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    HolidayService holidayService;

    @Autowired
    private EmailService emailService;

    @Autowired
    InterActiveTokensRepository interActiveTokensRepository;

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @PostConstruct
    @Scheduled(cron = "0 35 08 ? * *")
    public void bodScheduler() {
        ACTIVE_PROFILE = activeProfile;

        if (!shouldRunScheduler()) {
            return;
        }
        List<Map<String, String>> taskDetails = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));
        String envPrefix = activeProfile.equalsIgnoreCase("uatprod") ? "UAT Torus " : "Torus";
        StringBuilder errorDetails = new StringBuilder();
        try {
            logger.info("latest * ################################### BOD Scheduler started at: {} ############################################", LocalDateTime.now());
            logger.info("latest * ################################### started processRedisData Started at: {} ############################################", LocalDateTime.now());

            runTaskAndTrack("ProcessRedisData", () -> {
                redisFetchedData = processRedisData();
                if (redisFetchedData == null) {
                    throw new RuntimeException("Redis expiry dates data is empty");
                }
            }, taskDetails, formatter,errorDetails);

            logger.info("################################### completed processRedisData Started at: {} ############################################", LocalDateTime.now());

            logger.info("################################### Started dailyUpdateUserTradingModeAndFlushXtsTokens at: {} ############################################", LocalDateTime.now());

            runTaskAndTrack("DailyUpdateUserTradingModeAndFlushXtsTokens", this::dailyUpdateUserTradingModeAndFlushXtsTokens, taskDetails, formatter,errorDetails);

            logger.info("################################### completed dailyUpdateUserTradingModeAndFlushXtsTokens Completed at: {} ############################################", LocalDateTime.now());

            logger.info("################################### Started loadMasterResponseFO Completed at: {} ############################################", LocalDateTime.now());

            runTaskAndTrack("LoadMasterResponseFO", this::loadMasterResponseFO, taskDetails, formatter,errorDetails);

            logger.info("################################### completed loadMasterResponseFO Completed at: {} ############################################", LocalDateTime.now());


//            logger.info("################################### Started dailyUpdateStrategyStatus Completed at: {} ############################################", LocalDateTime.now());
//            dailyUpdateStrategyStatus();
//            logger.info("################################### completed dailyUpdateStrategyStatus Completed at: {} ############################################", LocalDateTime.now());

            logger.info("################################### BOD Scheduler Completed at: {} ############################################", LocalDateTime.now());

            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String formattedNow = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(formatter1);
            // Send success email
            String subject = envPrefix + " Signal BOD Scheduler Completed Successfully";
            String successMessage = subject + " at " + formattedNow + ".";
            try{
                String nextStep = "Platform is ready for operations. All systems are running in sync.";
                String message = emailService.getEmailSuccessBodTemplate(subject,successMessage,taskDetails,nextStep);
//                emailService.sendEmail( BOD_EMAILS, subject + " " + formattedNow,message);
            } catch (Exception mailEx) {
                logger.error("Error sending failure email: {}", mailEx.getMessage(), mailEx);
            }
        }catch (Exception e) {
            logger.error("Torus Signal BOD Scheduler failed: {}", e.getMessage(), e);
            String failureTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(formatter);
            String failureSubject = envPrefix + " Signal BOD Scheduler Failed";
            String failureMessage = "<p>" + failureSubject + " at " + failureTime + ".</p><p>Please review the task logs below.</p>" + errorDetails.toString();

            try {
                String nextStep = "Platform is not in sync. Please immediatly sync manually.";
                String message = emailService.getEmailSuccessBodTemplate(failureSubject, failureMessage, taskDetails,nextStep);
//                emailService.sendEmail( BOD_EMAILS, failureSubject + " " + failureTime , message);
            } catch (Exception mailEx) {
                logger.error("Error sending failure email: {}", mailEx.getMessage(), mailEx);
            }
        }
    }

    private void loadMasterResponseFO() {
        getRedisMasterResponseFO();
    }

    void getRedisMasterResponseFO() {
        try {
            List<MasterResponseFO> redisMasterResponse = new ArrayList<>();
            indexExpiryDates.entrySet().stream().filter((val)->!val.getKey().contains("_")) .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    HashMap::new
            )).forEach((key, value) -> {

                Map<String, List<MasterResponseFO>> masterResponseFOList = fetchAllMasterResponses(key, value);
                if (masterResponseFOList == null || masterResponseFOList.isEmpty()) {
                    logger.info("No master response found for key: {}", key);
                    return;
                }
                masterResponseFOList.forEach((time, masterResponseFO) -> {
                    if (masterResponseFO != null && !masterResponseFO.isEmpty()) {
                        redisMasterResponse.addAll(masterResponseFO);
                        Set<Integer> strikeList = new HashSet<>();
                        for (MasterResponseFO master : masterResponseFO) {
                            String suffix = "";
                            if (master.getOptionType() == null)
                                suffix = "FU";
                            else if (master.getOptionType().equals("3"))
                                suffix = "CE";
                            else if (master.getOptionType().equals("4"))
                                suffix = "PE";
                            else
                                suffix = "OTHERS";
                            strikeList.add(master.getStrikePrice());
                            String masterKey = master.getInstrumentType() + master.getContractExpiration() + master.getStrikePrice() + suffix;
                            redisMasterResponseFOData.put(masterKey, master);
                        }
                        ArrayList<Integer> sortedStrikesList = new ArrayList<>(strikeList);
                        Collections.sort(sortedStrikesList);
                        redisIndexStrikePrices.put(time, sortedStrikesList);
                    }
                });

            });
        } catch (Exception e) {
            logger.error("error in the getRedisMasterResponseFO ,"+e.getMessage());
        }
    }

    public Map<String, List<MasterResponseFO>> fetchAllMasterResponses(String key, ExpiryDatesDTO value) {
        System.out.println("Fetching master responses for key:  " + key);
        logger.info("Fetching master responses for key:  " + key);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        List<MasterResponseFO> redisMasterResponse = new ArrayList<>();
        try {
            String timeStamp = value.getCurrentWeek().format(formatter);
            Map<String, List<MasterResponseFO>> result = Stream.of(value.getCurrentWeek(), value.getNextWeek(), value.getCurrentMonth(), value.getNextMonth()).map((time) -> {
                String localDateTime = time.format(formatter);
                String finalKey = key + "_" + localDateTime;
                String returnKey = key+"_" + time.toLocalDate().toString();
                List<MasterResponseFO> limited = marketDataFetch.getMasterResponseFO(finalKey);
                if (limited != null && !limited.isEmpty()) {
                    return new AbstractMap.SimpleEntry<>(returnKey, limited);
                }
                return new AbstractMap.SimpleEntry<>(returnKey, limited);
            }) .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (v1, v2) -> v1,
                    HashMap::new
            ));
            return result;
        } catch (Exception e) {
            System.out.println("Error fetching Redis master data: " + e.getMessage());
            logger.error("Error fetching Redis master data, "+e.getMessage());
            return null;
        }
    }

    @Transactional
    public void dailyUpdateUserTradingModeAndFlushXtsTokens() {
        try {
            int updatedCount = userAuthConstantsRepository.updateUserTradingModeAndFlushXtsTokens(TradingMode.FORWARD.getKey());
            logger.info("Completed daily update. Updated user trading mode to {}", TradingMode.FORWARD.getKey());
        } catch (Exception e) {
            logger.error("Error in dailyUpdateUserTokenTypeStatus, "+e.getMessage());
        }
    }

    @Scheduled(cron = "1 00 00 ? * *")
    void dailyUpdateStrategyStatusScheduler() {

        boolean workingDay =  shouldRunScheduler();
        List<Map<String, String>> taskDetails = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));
        String envPrefix = activeProfile.equalsIgnoreCase("uatprod") ? "UAT Torus" : "Torus";
        StringBuilder errorDetails = new StringBuilder();
        AtomicReference<String> schedularSummary = new AtomicReference<>("");
        try {
            logger.info("################################### New Day Scheduler Started at: {} ############################################", LocalDateTime.now());
            logger.info("################################### started processRedisData Started at: {} ############################################", LocalDateTime.now());
            runTaskAndTrack("processRedisData", () -> redisFetchedData = processRedisData(), taskDetails, formatter, errorDetails);
            logger.info("################################### completed processRedisData Started at: {} ############################################", LocalDateTime.now());

            logger.info("################################### started dailyUpdateStrategyStatus Scheduler Started at: {} ############################################", LocalDateTime.now());

            runTaskAndTrack("dailyUpdateStrategyStatus", () -> schedularSummary.set(dailyUpdateStrategyStatus(workingDay)), taskDetails, formatter, errorDetails);
            logger.info("################################### New Day Scheduler Completed at: {} ############################################", LocalDateTime.now());
            logger.info("Daily update of strategy status completed successfully");
            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String formattedNow = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(formatter1);
            String subject = envPrefix + " Strategy Signal BOD Scheduler completed successfully";
            String successMessage = "Torus Signal BOD Scheduler completed successfully at " + formattedNow + ".<br>" + schedularSummary.get();
            System.out.println("---------------Strategy bod Success mail triggered--------------------");
            try {
                String nextStep = "Platform is ready for operations. All systems are running in sync.";
                String message = emailService.getEmailSuccessBodTemplate(subject, successMessage, taskDetails,nextStep);
//                emailService.sendEmail(BOD_EMAILS, subject + " " + formattedNow, message);
            } catch (Exception mailEx) {
                logger.error("Error sending strategy success email: {}", mailEx.getMessage(), mailEx);
            }
        } catch (Exception e) {
            logger.error("Torus Strategy Signal BOD Scheduler failed: {}", e.getMessage(), e);
            String failureTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(formatter);
            String failureSubject = envPrefix + " Strategy Signal BOD Scheduler Failed";
            String failureMessage = "<p>" + failureSubject + " at " + failureTime + ".</p><p>Please review the task logs below.</p>" + errorDetails.toString();

            try {
                String nextStep = "Platform is not in sync. Please immediatly sync manually.";
                String message = emailService.getEmailSuccessBodTemplate(failureSubject, failureMessage, taskDetails,nextStep);
//                emailService.sendEmail(BOD_EMAILS, failureSubject + " " + failureTime , message);
            } catch (Exception mailEx) {
                logger.error("Error sending failure email: {}", mailEx.getMessage(), mailEx);
            }
        }
    }
    @Transactional
    public String dailyUpdateStrategyStatus( boolean workingDay) {
        int total = 0;
        int updated = 0;
        int ignored = 0;
        String currentDayName = LocalDate.now().getDayOfWeek().name().substring(0, 1).toUpperCase()
                + LocalDate.now().getDayOfWeek().name().substring(1).toLowerCase();

        List<Strategy> allStrategies = strategyRepository.findStrategiesWithoutLiveSignals();
        total = allStrategies.size();

        for (Strategy strategy : allStrategies) {
            Hibernate.initialize(strategy.getEntryDetails());
            String expiryPeriod = strategy.getExpiry();
            String indexName = strategy.getUnderlying().getName();
            if (indexExpiryDates.containsKey(indexName)) {
                strategy.setStrikeDate(fetchStrikeDate(indexName, expiryPeriod));
            }
            if (!(StrategyOption.ENABLE_HOLD.getKey().equalsIgnoreCase(strategy.getHoldType()))) {
                ArrayList<String> availableDays = new ArrayList<>();
                if (strategy.getEntryDetails().getEntryDays() != null) {
                    for (EntryDays entryDay : strategy.getEntryDetails().getEntryDays()) {
                        availableDays.add(entryDay.getDay());
                    }
                }
                if (dayIsPresent(availableDays, currentDayName) && workingDay) {
                    strategy.setStatus(Status.ACTIVE.getKey());
                    updated++;
                } else if (!Status.INACTIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                    strategy.setStatus(Status.STANDBY.getKey());
                    ignored++;
                }
                strategy.setManualExitType(ManualExit.DISABLED.getKey());
                strategy.setSignalCount(0);
            }
        }
        strategyRepository.saveAllAndFlush(allStrategies);

        return String.format(
                "<p><strong>Strategy Summary:</strong> Total = %d, updated = %d, Ignored = %d</p>",
                total, updated, ignored
        );
    }

    LocalDateTime fetchStrikeDate(String indexName, String expiryPeriod){
        if (expiryPeriod.contains(ExpiryType.CURRENT_WEEK.getKey())){
            return indexExpiryDates.get(indexName).getCurrentWeek();
        }else if (expiryPeriod.contains(ExpiryType.NEXT_WEEK.getKey())){
            return indexExpiryDates.get(indexName).getNextWeek();
        }else if (expiryPeriod.contains(ExpiryType.CURRENT_MONTH.getKey())){
            return indexExpiryDates.get(indexName).getCurrentMonth();
        }else if (expiryPeriod.contains(ExpiryType.NEXT_MONTH.getKey())){
            return indexExpiryDates.get(indexName).getNextMonth();
        }
        return null;
    }

    public boolean dayIsPresent(ArrayList<String> allDays,String currentDay){

//        logger.info("inside the dayIsPresent alldays = "+allDays);
        for (String activeDay:allDays){
            if (activeDay.equalsIgnoreCase(currentDay) || (EXCEPTION_DATE != null && EXCEPTION_DATE.equals(LocalDate.now())))
                return true;
        }
        return false;

    }

    public Map<String,ArrayList<LocalDateTime>> processRedisData(){

        Map<String,ArrayList<LocalDateTime>> finalData = new HashMap<>();
        try {
            getRedisDates();
            for (Map.Entry<String, List<String>> indexEntry : redisFetchedIndexExpiry.entrySet()) {
                List<String> stringArray = indexEntry.getValue();
                ArrayList<LocalDateTime> dateTimeList = new ArrayList<>();
                for (String dateTimeStr : stringArray) {
                    dateTimeList.add(LocalDateTime.parse(dateTimeStr));
                }
                dateTimeList.sort((d1, d2) -> d1.compareTo(d2));
                finalData.put(indexEntry.getKey(), dateTimeList);
            }

            for (Map.Entry<String, ArrayList<LocalDateTime>> indexData : finalData.entrySet()) {
                ArrayList<LocalDateTime> dates = indexData.getValue();
                ExpiryDatesDTO expiryDatesDTO = new ExpiryDatesDTO();
                expiryDatesDTO.setCurrentWeek(indexData.getValue().get(0));
                expiryDatesDTO.setNextWeek(indexData.getValue().get(1));

                LocalDateTime lastDateInCurrentMonth = dates.stream()
                        .filter(date -> date.getYear() == expiryDatesDTO.getCurrentWeek().getYear() && date.getMonth() == expiryDatesDTO.getCurrentWeek().getMonth())
                        .max(Comparator.naturalOrder())
                        .orElse(null);

                LocalDateTime lastDateInNextMonth = dates.stream()
                        .filter(date -> date.isAfter(expiryDatesDTO.getCurrentWeek()) && (date.getMonthValue() != expiryDatesDTO.getCurrentWeek().getMonthValue() || date.getYear() != expiryDatesDTO.getCurrentWeek().getYear()))
                        .map(date -> LocalDateTime.of(date.getYear(), date.getMonth(), 1, 0, 0))
                        .distinct()
                        .sorted()
                        .findFirst()
                        .flatMap(nextMonth -> dates.stream()
                                .filter(date -> date.getYear() == nextMonth.getYear() && date.getMonth() == nextMonth.getMonth())
                                .max(Comparator.naturalOrder()))
                        .orElse(null);
                String indexName = getIndexName(indexData.getKey());
                expiryDatesDTO.setCurrentMonth(lastDateInCurrentMonth);
                expiryDatesDTO.setNextMonth(lastDateInNextMonth);
                logger.info("the current index = " + indexData.getKey());
                logger.info("the expiryDatedDTO = " + expiryDatesDTO.toString());
                indexExpiryDates.put(indexName, expiryDatesDTO);
            }
            return finalData;
        }catch (Exception e){
            logger.error("error with Redis Expiry Dates data, "+e.getMessage());
        }
        return null;
    }

    private String getIndexName(String indexName) {
        String finalName = indexName;
        String[] parts = indexName.split("_");
        if (parts.length > 2) {
            if (parts[2].equalsIgnoreCase("FUTIDX") || parts[2].equalsIgnoreCase("IF"))
                finalName = parts[1] + "_FUTURE";
            else
                finalName = parts[1];
        }
        return finalName;
    }

    void getRedisDates() {
        redisFetchedIndexExpiry = marketDataFetch.getInstrumentExpiryDates(EXPIRYVALUE);
    }

    public boolean shouldRunScheduler() {
        try {
//            if (holidayService.isTodayAHoliday()) {
//                System.out.println(" ----- Today is a holiday. Scheduler will not run ----- ");
//                return false;
//            }

            DayOfWeek day = LocalDate.now().getDayOfWeek();
            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
// && !holidayService.isTodayWorkingWeekend()
            if (isWeekend) {
                System.out.println(" ---- Today is a weekend and not a working day. Scheduler will not run. ---- ");
                return false;
            }
            if (activeProfile.equalsIgnoreCase("dev") || activeProfile.equalsIgnoreCase("test")) {
                System.out.println(" ---- Running in dev/test profile. Scheduler will not run. ---- ");
                return true;
            }

            return true;
        } catch (Exception e) {
            logger.error("Error while checking holiday. Proceeding with scheduler by default.", e);
            return true;
        }
    }

    @Scheduled(cron = "0 35 08 ? * *")
    public void flushRedisTokens(){
        try {
            logger.info("################################### Started redis token flush at: {} ############################################", LocalDateTime.now());

            Set<String> keys = interActiveTokensRepository.findAllInteractiveKeys();

            if (keys != null && !keys.isEmpty()) {
                keys.forEach(key -> {
                    interActiveTokensRepository.delete(key);
                });
            }
            logger.info("################################### Redis tokens flushed: {}, Completed at: {} ############################################", keys.size(), LocalDateTime.now());
        } catch (Exception e) {
            logger.error("Error flushing Redis tokens: " + e.getMessage());
            emailService.getEmailErrorTemplate("BOD Scheduler Error - Flushing Redis Tokens"+ e.getMessage());
        }
    }
    private void runTaskAndTrack(String taskName, Runnable task, List<Map<String, String>> taskDetails, DateTimeFormatter formatter,StringBuilder errorDetails) throws Exception {
        ZonedDateTime start = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        try {
            task.run();
            ZonedDateTime end = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
            taskDetails.add(Map.of(
                    "taskName", taskName,
                    "startTime", start.format(formatter),
                    "endTime", end.format(formatter),
                    "status", "Success"
            ));
        } catch (Exception ex) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
            taskDetails.add(Map.of(
                    "taskName", taskName,
                    "startTime", now.format(formatter),
                    "endTime", now.format(formatter),
                    "status", "Failed"
            ));
            errorDetails.append("<p><strong>Failure in task:</strong> ")
                    .append(taskName)
                    .append("</p><p><strong>Reason:</strong> ")
                    .append(ex.getMessage())
                    .append("</p>");
            throw new RuntimeException("Task failed: " + taskName, ex);
        }
    }
}
