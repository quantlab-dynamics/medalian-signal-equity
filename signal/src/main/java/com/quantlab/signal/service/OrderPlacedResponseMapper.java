package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.OrderLegNotFoundException;
import com.quantlab.common.exception.custom.SignalNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.OmsType;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
public class OrderPlacedResponseMapper {


    private static final Logger logger = LogManager.getLogger(OrderPlacedResponseMapper.class);

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    AppUserRepository appUserRepository;

//    @Autowired
//    EmailService emailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order mapToOrder(com.market.proto.xts.OrderBookResponseStream responseStream) {
        Order order = new Order();
        StrategyLeg strategyLeg = null;
        try {
            Optional<Order> existingOrder = orderRepository.getByAppOrderID(responseStream.getAppOrderID());
            if (existingOrder.isPresent())
                order = existingOrder.get(); //makes the objects have same address

            order.setLoginID(responseStream.getLoginID());
            order.setAppOrderID(responseStream.getAppOrderID());
            order.setOrderReferenceID(responseStream.getOrderReferenceID());
            order.setGeneratedBy(responseStream.getGeneratedBy());
            order.setExecutionType(responseStream.getOrderType());
            order.setExchangeOrderId(responseStream.getExchangeOrderID());
            order.setOrderCategoryType(responseStream.getOrderCategoryType());
            order.setOrderSide(responseStream.getOrderSide());
            order.setOrderType(responseStream.getOrderType());
            order.setProductType(responseStream.getProductType());
            order.setTimeInForce(responseStream.getTimeInForce());
            order.setPrice((long) responseStream.getOrderPrice());
            order.setQuantity((long) responseStream.getOrderQuantity());
            order.setOrderStopPrice((long) responseStream.getOrderStopPrice());
            order.setStatus(responseStream.getOrderStatus());
            order.setAverageTradedPrice(responseStream.getOrderAverageTradedPrice());
            order.setLeavesQuantity((long) responseStream.getLeavesQuantity());
            order.setCumulativeQuantity((long) responseStream.getCumulativeQuantity());
            order.setDisclosed_uantity((long) responseStream.getOrderDisclosedQuantity());
            order.setGeneratedDateTime(responseStream.getOrderGeneratedDateTime());
            order.setExchangeTransactTime(responseStream.getExchangeTransactTime());
            order.setLastUpdateTime(responseStream.getLastUpdateDateTime());
            order.setExpiryDate(responseStream.getOrderExpiryDate());
            order.setCancelRejectReason(responseStream.getCancelRejectReason());
            order.setOrderUniqueIdentifier(responseStream.getOrderUniqueIdentifier());
            order.setLegStatus(responseStream.getOrderLegStatus());
            order.setMessageCode((long) responseStream.getMessageCode());
            order.setMessageVersion(String.valueOf(responseStream.getMessageVersion()));
            order.setTokenID(String.valueOf(responseStream.getTokenID()));
            order.setApplicationType((long) responseStream.getApplicationType());
            order.setUniqueKey(responseStream.getUniqueKey());
            order.setExchangeInstrumentId((long) responseStream.getExchangeInstrumentID());
            order.setDeployedOn(Instant.parse(responseStream.getExchangeTransactTime()));
            order.setExchangeSegment(responseStream.getExchangeSegment());
            if (!responseStream.getOrderUniqueIdentifier().isEmpty()){
                order.setUniqueKey(responseStream.getUniqueKey());
                //the response is expected to be QO_signalId_strategyLegId
                String[] parts = responseStream.getOrderUniqueIdentifier().split("_");
//                Arrays.stream(parts).forEach(System.out::println);
                String signalId = parts[1];
                String legId = parts[2];
                Long l1 =Long.parseLong(signalId.trim());
                Long l2 =Long.parseLong(legId.trim());
                Optional<Signal> orderSignal =  signalRepository.findById(l1);
                Optional<StrategyLeg> orderLeg =  strategyLegRepository.findById(l2);
                if(orderSignal.isEmpty()){
                    throw new SignalNotFoundException("Signal not found for the given signal id : "+ responseStream.getOrderUniqueIdentifier());
                }
                if(orderLeg.isEmpty()){
                    throw new OrderLegNotFoundException("Strategy leg not found for the given leg id : "+ responseStream.getOrderUniqueIdentifier());
                }
                strategyLeg = orderLeg.get();
                order.setSignal(orderSignal.get());
                order.setAppUser(orderSignal.get().getAppUser());
                order.setStrategy(orderSignal.get().getStrategy());
                order.setUserAdmin(orderSignal.get().getAppUser().getAdmin());
                order.setUnderlying(orderSignal.get().getStrategy().getUnderlying().getName());
                order.setInstrumentName(orderLeg.get().getName());
                order.setSourceType(OmsType.OMS_XTS.getOmsType());
            }
            processStrategyBasedOnOrder(order, strategyLeg);
            return order;
        } catch (RuntimeException e) {
           System.err.println("unable to OrderPlacedResponseMapper "+e.getMessage());
//           e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Transactional
    public void processStrategyBasedOnOrder(Order order, StrategyLeg strategyLeg) {
        if (order.getStatus().equalsIgnoreCase(StrategyStatus.REJECTED.getKey())){
            orderFailureHandler(order, strategyLeg);
        }else {
            strategyLeg.setStatus(Status.LIVE.getKey());
        }
        strategyLeg.setFilledQuantity(order.getCumulativeQuantity());
        strategyLegRepository.save(strategyLeg);
    }

    @Transactional
    public void orderFailureHandler(Order order, StrategyLeg strategyLeg) {
        if (strategyLeg != null){
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setDescription(new ArrayList<>(List.of(order.getCancelRejectReason())));
            deploymentErrors.setStrategy(strategyLeg.getStrategy());
            deploymentErrors.setAppUser(strategyLeg.getAppUser());
            deploymentErrors.setErrorCode(order.getMessageCode().toString());
            deploymentErrors.setStatus(order.getStatus());
            deploymentErrors.setDeployedOn(order.getDeployedOn());
            deploymentErrors.getDescription().add(order.getCancelRejectReason());
            Strategy strategy = order.getStrategy();
            strategy.setStatus(Status.ERROR.getKey());
            strategyRepository.save(strategy);
            deploymentErrors.setStrategyLeg(strategyLeg);
            deploymentErrorsRepository.save(deploymentErrors);
            strategyLeg.setStatus(Status.ERROR.getKey());
            strategyLeg.getSignal().setStatus(Status.ERROR.getKey());
            String errorDetails = order.getCancelRejectReason();
            String toEmail = strategyLeg.getAppUser().getAdmin().getEmail();
//            String message = emailService.getEmailErrorTemplate(errorDetails);
//            emailService.sendEmail(toEmail, "Order Exit Error", message);
        }
    }
}

