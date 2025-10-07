package com.quantlab.signal.service;

import com.quantlab.common.emailService.EmailService;
import com.quantlab.common.entity.*;
import com.quantlab.common.repository.DeploymentErrorsRepository;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.LegStatus;
import com.quantlab.common.utils.staticstore.dropdownutils.LegType;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Component
public class OrderCommonService {
    private static final Logger logger = LogManager.getLogger(OrderCommonService.class);

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    EmailService emailService;

    @Transactional
    public void processStrategyBasedOnOrder(Order order, StrategyLeg strategyLeg, Signal  orderSignal) {
        boolean sucessfullyFilled = false;
        if ( (FAILED_STATUS_LIST.contains(order.getStatus())
                || Long.valueOf(11221122L).equals(order.getMessageCode())) &&
                !SAFE_STATUS_LIST.contains(order.getStatus())) {
            logger.info("processStrategyBasedOnOrder: Order failed for order: {}, status: {}, leg id : {}, rejectReason = {}", order.getAppOrderID() ,order.getStatus(), strategyLeg.getId(), order.getCancelRejectReason());
            orderFailureHandler(order, strategyLeg);
        }else {
            logger.info("processStrategyBasedOnOrder: Processing strategy leg for order: {} leg id : {}", order.getAppOrderID() , strategyLeg.getId());
            sucessfullyFilled = orderSuccessChecker(order, strategyLeg);
            if (sucessfullyFilled){
                logger.info("added order price for ledID: {}, orderPrice: {} ",strategyLeg.getId(), order.getPrice());
                strategyLeg.setPrice(order.getPrice());
                if (strategyLeg.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())) {
                    logger.info("processStrategyBasedOnOrder: strategy leg converted to live: {} leg id : {}", order.getAppOrderID() , strategyLeg.getId());
                    strategyLeg.setExecutedPrice(order.getPrice());
                    strategyLeg.setStatus(Status.LIVE.getKey());
                }
                else {
                    logger.info("processStrategyBasedOnOrder: strategy leg converted to exit: {} leg id : {}", order.getAppOrderID() , strategyLeg.getId());
                    strategyLeg.setPrice(order.getPrice());
                    strategyLeg.setExecutedPrice(order.getPrice());
                    strategyLeg.setStatus(Status.EXIT.getKey());
                }
            }
        }
        strategyLeg.setFilledQuantity(order.getCumulativeQuantity());
        strategyLegRepository.saveAndFlush(strategyLeg);
        logger.info("processStrategyBasedOnOrder: strategy leg saved");

        if (sucessfullyFilled){
            Optional<Strategy> strategyOptional = strategyRepository.findById(order.getStrategy().getId());
            if (strategyOptional.isEmpty()) {
                logger.error("processStrategyBasedOnOrder: Strategy not found for id: {}", order.getStrategy().getId());
                return;
            }
            Strategy strategy = strategyOptional.get();
            String status = checkStrategyStatus(orderSignal);

            if (status.equalsIgnoreCase(Status.LIVE.getKey())) {
                orderSuccessfulLog(strategy, order, strategyLeg, status, "Strategy has been successfully Placed");
                orderSignal.setStatus(Status.LIVE.getKey());
                signalRepository.updateSignalStatus(orderSignal.getId(), Status.LIVE.getKey());
                logger.info("updated signal of strategyID {}, to status: {} ",order.getStrategy().getId(), status);
            }
            else if (Status.EXIT.getKey().equalsIgnoreCase(status)) {
                orderSuccessfulLog(strategy, order, strategyLeg, status, "Strategy has been successfully Exited");
                orderSignal.setStatus(Status.EXIT.getKey());
                logger.info("updated signal of strategyID {}, to status: {} ",order.getStrategy().getId(), status);
                signalRepository.updateSignalStatus(orderSignal.getId(), Status.EXIT.getKey());
            }

            logger.info("processStrategyBasedOnOrder: successfullyFilled  strategyLeg id :{}, strategy status: {}. ",strategyLeg.getId(), status);
            strategy.setStatus(status);
            strategyRepository.updateStrategyStatus(strategy.getId(), strategy.getStatus());
        }
    }

    public boolean orderSuccessChecker(Order order, StrategyLeg strategyLeg) {
        return Objects.equals(order.getCumulativeQuantity(), strategyLeg.getQuantity());
    }


    @Transactional
    public String checkStrategyStatus(Signal orderSignal) {
        boolean isAllLegsLive = strategyLegRepository.isAllLegsStatusBySignalId(orderSignal.getId(), Status.LIVE.getKey());
        logger.info("checkStrategyStatus: Checking strategy status for signal id: {} , isAllLegsLive is {}", orderSignal.getId(), isAllLegsLive);
        if (isAllLegsLive) {
            if (orderSignal.getStatus().equalsIgnoreCase(Status.ERROR.getKey())) {
                logger.info("checkStrategyStatus:found  strategy status as Error for strategy id: {} , isAllLegsLive is {}", orderSignal.getStrategy().getId(), isAllLegsLive);
                return strategyIsError(orderSignal);
            }
            logger.info("checkStrategyStatus: changing strategy status for strategy id to Live: {} , isAllLegsLive is {}", orderSignal.getStrategy().getId(), isAllLegsLive);
            return Status.LIVE.getKey();
        }

        boolean isAllLegsExit = strategyLegRepository.isAllLegsStatusBySignalId(orderSignal.getId(), Status.EXIT.getKey());
        logger.info("checkStrategyStatus: Checking strategy status for signal id: {} , isAllLegsExit is {}", orderSignal.getId(), isAllLegsExit);
        if (isAllLegsExit) {
            if (orderSignal.getStatus().equalsIgnoreCase(Status.ERROR.getKey()))
                return strategyIsError(orderSignal);

            return Status.EXIT.getKey();
        }

        boolean adjustmentCompleted = isSignalAdjusted(orderSignal);
        if (adjustmentCompleted)
            return Status.LIVE.getKey();

        boolean isNotError = strategyLegRepository.noLegHasStatusBySignalId(orderSignal.getId(), Status.ERROR.getKey());
        logger.info("checkStrategyStatus: Checking strategy status for signal id: {} , isNotError is {}", orderSignal.getId(), isNotError);
        if (isNotError)
            return Status.PENDING.getKey();
        else
            return Status.ERROR.getKey();
    }

    private boolean isSignalAdjusted(Signal orderSignal) {
        logger.info("isSignalAdjusted: Checking if signal is adjusted for signal id: {}", orderSignal.getId());
        for (StrategyLeg signalLeg : orderSignal.getSignalLegs()) {
            if(signalLeg.getFilledQuantity() == null || !signalLeg.getFilledQuantity().equals(signalLeg.getQuantity())) {
                logger.info("isSignalAdjusted: Signal leg with id {} is not adjusted, filled quantity: {}, expected quantity: {}", signalLeg.getId(), signalLeg.getFilledQuantity(), signalLeg.getQuantity());
                return false;
            }
        }
        logger.info("isSignalAdjusted: All signal legs are filled for signal id: {}", orderSignal.getId());
        return true;
    }

    @Transactional
    public String strategyIsError(Signal orderSignal) {
        if (orderSignal.getStatus().equalsIgnoreCase(Status.ERROR.getKey())) {
            if (signalIsExit(orderSignal)) {
                logger.info("checkStrategyStatus: changing signal status for signal id to Live: {} ", orderSignal.getId());
                orderSignal.setStatus(Status.EXIT.getKey());
            }
            else {
                logger.info("checkStrategyStatus: changing strategy status for signal id to Live: {} ", orderSignal.getId());
                orderSignal.setStatus(Status.LIVE.getKey());
            }
            signalRepository.updateSignalStatus(orderSignal.getId(), orderSignal.getStatus());
        }
        logger.info("strategyIsError final status returned = {}",orderSignal.getStatus());

        return orderSignal.getStatus();
    }

    @Transactional
    public boolean signalIsExit(Signal orderSignal) {
        List<Object[]> results = strategyRepository.countLegsByTwoTypesForSignal(orderSignal.getId(), LegStatus.TYPE_EXIT.getKey(), LegStatus.TYPE_OPEN.getKey());
        Map<String, Long> typeCountMap = results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
        return Objects.equals(typeCountMap.get(LegStatus.TYPE_EXIT.getKey()), typeCountMap.get(LegStatus.TYPE_EXIT.getKey()));
    }


    @Transactional
    public void orderFailureHandler(Order order, StrategyLeg strategyLeg) {
        if (strategyLeg != null){
            Strategy strategy = strategyLeg.getStrategy();
            boolean wasAlreadyError = Status.ERROR.getKey().equals(strategy.getStatus());
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setDescription(new ArrayList<>(List.of(order.getCancelRejectReason())));
            deploymentErrors.setStrategy(strategyLeg.getStrategy());
            deploymentErrors.setAppUser(strategyLeg.getAppUser());
            deploymentErrors.setErrorCode(order.getMessageCode().toString());
            deploymentErrors.setStatus(order.getStatus());
            deploymentErrors.setDeployedOn(order.getDeployedOn());
            deploymentErrors.getDescription().add(order.getCancelRejectReason());
            strategy = order.getStrategy();
            strategy.setStatus(Status.ERROR.getKey());
            strategyRepository.save(strategy);
            deploymentErrors.setStrategyLeg(strategyLeg);
            deploymentErrorsRepository.save(deploymentErrors);
            strategyLeg.setStatus(Status.ERROR.getKey());
            strategyLeg.getSignal().setStatus(Status.ERROR.getKey());
            if (!wasAlreadyError) {
                try {
//                    emailService.sendStrategyErrorEmail(strategy,
//                            "Leg failure on leg ID " + strategyLeg.getId() +
//                                    " — reason: " + order.getCancelRejectReason());
                    logger.info("Sent strategy error email for strategy ID {}", strategy.getId());
                } catch (Exception ex) {
                    logger.error("Failed to send strategy error email for {}: {}",
                            strategy.getId(), ex.getMessage());
                }
            }
        }
    }

    @Transactional
    public void orderSuccessfulLog(Strategy strategy, Order order, StrategyLeg strategyLeg, String status, String message) {
        try {
            if (strategyLeg != null) {
                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setDescription(Collections.singletonList(message));
                deploymentErrors.setStrategy(strategy);
                deploymentErrors.setAppUser(strategyLeg.getAppUser());
                deploymentErrors.setStatus(status);
                deploymentErrors.setDeployedOn(order.getDeployedOn());
                deploymentErrors.setStrategyLeg(strategyLeg);
                deploymentErrorsRepository.save(deploymentErrors);
            }
        } catch (Exception e) {
            logger.error("Error while logging successful order leg {}, status = {}, : {}",strategyLeg.getId(), status, e.getMessage());
        }
    }
}
