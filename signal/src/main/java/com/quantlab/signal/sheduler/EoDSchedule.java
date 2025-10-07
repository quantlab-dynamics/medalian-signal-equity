package com.quantlab.signal.sheduler;

import com.quantlab.common.dto.StatisticsResponseDto;
import com.quantlab.common.dto.StatisticsResponseObjDto;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.DeploymentErrors;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.AppUserRepository;
import com.quantlab.common.repository.DeploymentErrorsRepository;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.service.StatisticsService;
import com.quantlab.common.utils.StatisticsUtils;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.signal.service.GrpcErrorService;
import jakarta.annotation.PostConstruct;
import org.checkerframework.checker.units.qual.N;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.common.utils.staticstore.AppConstants.ERROR_STRATEGY_LIVE_AFTER_EOD_DESCRIPTION;

@Service
public class EoDSchedule {

    @Autowired
    BodSchedule bodSchedule;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    GrpcErrorService grpcErrorService;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    private StatisticsService statisticsService;


    private static final Logger logger = LoggerFactory.getLogger(EoDSchedule.class);

    @Scheduled(cron = "0 28 15 ? * *")
    public void rmsSquareOff() {
        try {
            intraDayRmsSquareOff();
        } catch (Exception e) {
            logger.error("############################ Error during RMS square off processing: {}. ############################", e.getMessage());
        }
    }

    private void intraDayRmsSquareOff() {
        try {
            rMSSquareOffSignals();
            rMSSquareOffStrategies();
        } catch (Exception e) {
            logger.error("Error during intra-day RMS square off: {}", e.getMessage());
        }

    }

    @Transactional
    public void rMSSquareOffStrategies() {
        List<Strategy> strategies = strategyRepository.findAllByStatusInAndPositionType(LIVE_ERROR, StrategyType.INTRADAY.getKey());
        List<DeploymentErrors> deploymentErrorsList = new ArrayList<>();
        for (Strategy strategy : strategies) {
            try {
                strategy.setStatus(Status.ERROR.getKey());

                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStrategy(strategy);
                deploymentErrors.setAppUser(strategy.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setStatus(strategy.getStatus());
                deploymentErrors.setDescription(new ArrayList<>(List.of(ERROR_STRATEGY_LIVE_AFTER_EOD_DESCRIPTION)));
                deploymentErrorsList.add(deploymentErrors);
            } catch (Exception e) {
                logger.error("Error when saving RMS square off logs for strategy = {} , error message is = {}",
                        strategy.getId(), e.getMessage());
            }
        }
        if (strategies.isEmpty()) {
            logger.info("No strategies found for RMS square off at EOD.");
            return;
        }
        logger.info("Found {} strategies for RMS square off at EOD.", strategies.size());
        strategyRepository.bulkUpdateStatus(Status.ERROR.getKey(), StrategyType.INTRADAY.getKey(), LIVE_ERROR);
        deploymentErrorsRepository.saveAll(deploymentErrorsList);
    }

    @Transactional
    public void rMSSquareOffSignals() {
        int total = signalRepository.bulkUpdateStatus(Status.RMS_ERROR.getKey(), StrategyType.INTRADAY.getKey(), LIVE_ERROR);
        logger.info("rMSSquareOffSignals: Total {} signals updated to RMS_ERROR status for RMS square off at EOD.", total);

    }

    @Transactional
    public void findSignalsForRmsSquareOff() {
        List<Signal> allSignals = signalRepository.findAllByStatusInAndPositionType(LIVE_ERROR, StrategyType.INTRADAY.getKey());
        if (allSignals.isEmpty()) {
            logger.info("No signals found for RMS square off at EOD.");
            return;
        }
        logger.info("Found {} signals for RMS square off at EOD.", allSignals.size());

        for (Signal signal : allSignals) {
            if (Objects.equals(signal.getPositionType(), StrategyType.INTRADAY.getKey())) {
                if (signal.getStatus().equalsIgnoreCase(Status.LIVE.getKey()))
                    exitFailedErrorLogs(ERROR_SIGNAL_LIVE_AFTER_EOD_DESCRIPTION, signal, SignalStatus.EXIT.getKey());
                else
                    exitFailedErrorLogs(ERROR_SIGNAL_EOD_DESCRIPTION, signal, SignalStatus.EXIT.getKey());
            }
        }
    }

    @Transactional
    public void eachStrategyStatusValidation() {
        List<Strategy> subscribedStrategies = strategyRepository.findAllBySubscriptionAndSourceNotNull(SubscriptionStatus.START.getKey());
        for (Strategy strategy : subscribedStrategies) {
            List<Signal> allSignals = signalRepository.findAllByStrategyAndStatusIn(strategy, LIVE_ERROR);
            for (Signal signal : allSignals) {
                if (Objects.equals(signal.getPositionType(), StrategyType.INTRADAY.getKey())) {
                    if (signal.getStatus().equalsIgnoreCase(Status.LIVE.getKey()))
                        exitFailedErrorLogs(ERROR_SIGNAL_LIVE_AFTER_EOD_DESCRIPTION, signal, RUN_TIME_EXCEPTION);
                    else
                        exitFailedErrorLogs(ERROR_SIGNAL_EOD_DESCRIPTION, signal, RUN_TIME_EXCEPTION);
                }
            }
        }
    }

    @Transactional
    public void exitFailedErrorLogs(String errorMessage, Signal signal, String status) {
        try {
            if (!status.equalsIgnoreCase(SignalStatus.EXIT.getKey()))
                saveStrategyAndSignalAsError(signal);
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(signal.getStrategy());
            deploymentErrors.setAppUser(signal.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrors.setStatus(signal.getStrategy().getStatus());
            deploymentErrors.setDescription(new ArrayList<>(List.of(errorMessage)));
            deploymentErrorsRepository.save(deploymentErrors);
        } catch (Exception e) {
            logger.error("error when saving EOD signal live logs for strategy = {} ," +
                    " error message is = {}", signal.getStrategy().getId(), errorMessage);
        }

    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveStrategyAndSignalAsError(Signal signal) {

        signal.setStatus(Status.RMS_ERROR.getKey());
        signalRepository.save(signal);
        if (signal.getStrategy().getPositionType().equalsIgnoreCase(StrategyType.INTRADAY.getKey())) {
            Strategy strategy = signal.getStrategy();
            strategy.setStatus(Status.ERROR.getKey());
            strategyRepository.save(strategy);
        }
    }

    void eodScheduled(){
        try {
            Map<Long, Long> defaultStatistics = new HashMap<>();
            List<Long> subscribedStrategies = findAllSubscribedStrategies();

            AppUser firstUser = findFirstUser();
            Map<Long, Long> allStatistics = processSubscribedStrategies(subscribedStrategies);

            if (firstUser != null)
                defaultStatistics = getDefaultStatistics(firstUser, allStatistics);

            List<Strategy> allStrategies = strategyRepository.findAll();
            assignDrawDownToAllStrategies(allStrategies,defaultStatistics, allStatistics);
        } catch (Exception e) {
            logger.error("Error during EOD processing: {}", e.getMessage());
        }

    }

    private Map<Long, Long> getDefaultStatistics(AppUser firstUser, Map<Long, Long> allStatistics) {
        List<Strategy> firstUserStrategies = strategyRepository.findAllByAppUser_Id(firstUser.getId());
        Map<Long, Long> firstUserStatistics = new HashMap<>();
        for (Strategy strategy : firstUserStrategies) {
            try {
                Long stats = allStatistics.get(strategy.getId());
                if (stats != null) {
                    firstUserStatistics.put(strategy.getSourceId(), stats);
                } else {
                    logger.warn("No statistics found for strategy ID: {}", strategy.getId());
                }
            } catch (Exception e) {
                logger.error("Error creating statistics for strategy ID {} : {}", strategy.getId(), e.getMessage());
            }
        }
        return  firstUserStatistics;
    }

    private void assignDrawDownToAllStrategies(List<Strategy> allStrategies, Map<Long, Long> firstUserStatistics, Map<Long, Long> statistics) {
        for (Strategy strategy : allStrategies) {
            try {
                Long drawDown = statistics.get(strategy.getId());

                if(firstUserStatistics.containsKey(strategy.getId())) {
                    drawDown = firstUserStatistics.get(strategy.getId());
                }else if (drawDown == null && strategy.getSubscription() != null &&
                        !strategy.getSubscription().equalsIgnoreCase(SubscriptionStatus.START.getKey())) {
                    drawDown = firstUserStatistics.get(strategy.getSourceId());
                }

                if (drawDown != null) {
                    strategy.setDrawDown(drawDown);
                    strategyRepository.save(strategy);
                }
                else {
                    logger.warn("No statistics found for strategy ID: {}", strategy.getId());
                }
            } catch (Exception e) {
                logger.error("Error processing strategy ID {}: {}", strategy.getId(), e.getMessage());
            }
        }
    }

    private Map<Long, Long> processSubscribedStrategies(List<Long> subscribedStrategiesIds) {
        Map<Long, StatisticsResponseDto<Number>> statistics =  new HashMap<>();
        Map<Long, Long> allStrategyDrawDownList =  new HashMap<>();

        for (Long strategyId : subscribedStrategiesIds) {
            try {
                StatisticsResponseDto<Number> stats = statisticsService.getStatistics(strategyId);
                if (stats != null) {
                    for(StatisticsResponseObjDto<Number> stat : stats.getStatistics()){
                        if(stat.getName().equalsIgnoreCase(StatisticsMainTable.MAX_DRAWDOWN_PERCENT.getLabel())){
                            allStrategyDrawDownList.put(strategyId, stat.getValue().longValue());
                            break;
                        }
                    }

                    statistics.put(strategyId, stats);
                } else {
                    logger.warn("No statistics found for strategy ID: {}", strategyId);
                }
            } catch (Exception e) {
                logger.error("Error creating statistics for strategy ID {} : {}", strategyId, e.getMessage());
            }
        }
        return allStrategyDrawDownList;
    }

    private List<Long> findAllSubscribedStrategies() {
        List<Long> strategies = strategyRepository.findIdsBySubscription(SubscriptionStatus.START.getKey());
        if (strategies == null || strategies.isEmpty()) {
            logger.info("No subscribed strategies found for EOD processing.");
            return Collections.emptyList();
        }
        return strategies;
    }

    private AppUser findFirstUser() {
        try {
            AppUser appUser = appUserRepository.findByUserRole_id(4L).get();
            return appUser;
        }catch (Exception e) {
            logger.warn("No user found with role ID 4, returning null for EOD processing.");
            return null;
        }
    }
}
