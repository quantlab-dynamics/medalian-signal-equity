package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.OrderLegNotFoundException;
import com.quantlab.common.exception.custom.SignalNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import com.quantlab.common.utils.staticstore.dropdownutils.OmsType;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
public class OrderStatusFeedMapper {

    private static final Logger logger = LogManager.getLogger(OrderStatusFeedMapper.class);

    private static final Map<String, Integer> STATUS_PRECEDENCE = Map.of(
            "Pending", 1,
            "Modified", 2,
            "Part-Traded", 3,
            "Traded", 4,
            "Rejected", 5
    );
    private static final DateTimeFormatter fallbackFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    OrderCommonService orderCommonService;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order mapToOrder(com.market.proto.tr.OrderStatusFeed orderStatusFeed) {
        Order order = new Order();

        try {
            order.setAppOrderID(orderStatusFeed.getSOrderNumber());
            order.setExchangeOrderId(orderStatusFeed.getSExchOrderNumber());
            order.setOrderSide(mapTransactionTypeToOrderSide(orderStatusFeed.getCBuyOrSell()));
            order.setOrderType(orderStatusFeed.getSOrderType());
            order.setProductType(orderStatusFeed.getSProductName());
            double price = orderStatusFeed.getFPrice();
            if (price == 0.0) {
                price = orderStatusFeed.getFAvgTradePrice();
            }
            order.setPrice((long) (price * AMOUNT_MULTIPLIER));

            order.setQuantity((long) orderStatusFeed.getITotalQty());
            order.setExecutionType(orderStatusFeed.getSOrderType().equalsIgnoreCase("LMT") ? "Limit" : "Market");
            order.setStatus(orderStatusFeed.getSStatus());
            order.setAverageTradedPrice(String.valueOf(orderStatusFeed.getFAvgTradePrice() * AMOUNT_MULTIPLIER));
            order.setLeavesQuantity((long) orderStatusFeed.getITotalQtyRem());
            order.setCumulativeQuantity((long) (orderStatusFeed.getITotalQty() - orderStatusFeed.getITotalQtyRem()));
            order.setDisclosed_uantity(0L); // Not available
            order.setExchangeTransactTime(orderStatusFeed.getSExchOrderTime());
            order.setLastUpdateTime(orderStatusFeed.getSLastUpdatedTime());
            order.setOrderUniqueIdentifier(orderStatusFeed.getOrderUniqueIdentifier());
            order.setMessageCode(0L); // Not available
            order.setUniqueKey(orderStatusFeed.getSOrderNumber());
            order.setCancelRejectReason(orderStatusFeed.getSReasonDesc());
            order.setExchangeSegment(orderStatusFeed.getSExcgId());

            // Parse deployedOn timestamp
            order.setDeployedOn(Optional.ofNullable(orderStatusFeed.getSExchOrderTime())
                    .filter(s -> !s.isBlank())
                    .map(s -> {
                        try {
                            return LocalDateTime.parse(s).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
                        } catch (DateTimeParseException e1) {
                            try {
                                return LocalDateTime.parse(s, fallbackFormatter).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
                            } catch (DateTimeParseException e2) {
                                logger.warn("Unable to parse SExchOrderTime '{}'", s, e2);
                                return Instant.now();
                            }
                        }
                    })
                    .orElse(Instant.now()));

            order.setExchangeInstrumentId(Optional.of(orderStatusFeed.getSSecurityId())
                    .filter(s -> !s.isBlank())
                    .map(Long::parseLong)
                    .orElse(0L));

            order.setInstrumentName(orderStatusFeed.getSCustomSym());
            order.setTokenID(orderStatusFeed.getSSecurityId());

            if ("Rejected".equalsIgnoreCase(orderStatusFeed.getSStatus())) {
                order.setCancelRejectReason(Optional.of(orderStatusFeed.getSReasonDesc())
                        .filter(s -> !s.isBlank())
                        .orElse("Order rejected"));
            }

            // Link orderUniqueIdentifier to Signal and StrategyLeg if available
            if (orderStatusFeed.getOrderUniqueIdentifier() != null && !orderStatusFeed.getOrderUniqueIdentifier().isEmpty()) {
                order.setUniqueKey(orderStatusFeed.getOrderUniqueIdentifier());
                String[] parts = orderStatusFeed.getOrderUniqueIdentifier().split("_");
                if (parts.length >= 3) {
                    String signalIdStr = parts[1];
                    String legIdStr = parts[2];
                    Long signalId = Long.parseLong(signalIdStr.trim());
                    Long legId = Long.parseLong(legIdStr.trim());

                    Optional<Signal> orderSignalOptional = signalRepository.findById(signalId);
                    Optional<StrategyLeg> orderLegOptional = strategyLegRepository.findById(legId);

                    if (orderSignalOptional.isEmpty()) {
                        throw new SignalNotFoundException("Signal not found for id: " + signalId);
                    }
                    if (orderLegOptional.isEmpty()) {
                        throw new OrderLegNotFoundException("Strategy leg not found for id: " + legId);
                    }

                    Signal orderSignal = orderSignalOptional.get();
                    StrategyLeg strategyLeg = orderLegOptional.get();

                    order.setSignal(orderSignal);
                    order.setAppUser(orderSignal.getAppUser());
                    order.setStrategy(orderSignal.getStrategy());
                    order.setUserAdmin(orderSignal.getAppUser().getAdmin());
                    order.setUnderlying(orderSignal.getStrategy().getUnderlying().getName());
                    order.setInstrumentName(strategyLeg.getName());
                    order.setSourceType(OmsType.OMS_TR.getOmsType());
                }
            }

            return order;
        } catch (Exception e) {
            logger.error("Error mapping OrderStatusFeed: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Save or update order with status precedence check and then process strategy.
     */
    @Transactional
    public void saveOrUpdateOrderWithPrecedenceAndProcessStrategy(Order incomingOrder) {
        String orderId = incomingOrder.getAppOrderID();
        Optional<Order> existingOrderOpt = orderRepository.findByAppOrderIDForUpdate(orderId);

        if (existingOrderOpt.isPresent()) {
            Order existingOrder = existingOrderOpt.get();

            int currentRank = STATUS_PRECEDENCE.getOrDefault(existingOrder.getStatus(), 0);
            int incomingRank = STATUS_PRECEDENCE.getOrDefault(incomingOrder.getStatus(), 0);

            if (incomingRank >= currentRank) {

                updateOrderFields(existingOrder, incomingOrder);

                orderRepository.save(existingOrder);

                orderCommonService.processStrategyBasedOnOrder(existingOrder,
                        findStrategyLeg(existingOrder),
                        existingOrder.getSignal());

                logger.info("Order {} updated to status {}, strategy processed", orderId, existingOrder.getStatus());
            } else {
                logger.info("Ignored stale update for order {}. Incoming status: {}, existing status: {}",
                        orderId, incomingOrder.getStatus(), existingOrder.getStatus());
            }
        } else {
            // New order - save and process
            orderRepository.save(incomingOrder);

            orderCommonService.processStrategyBasedOnOrder(incomingOrder,
                    findStrategyLeg(incomingOrder),
                    incomingOrder.getSignal());

            logger.info("New order {} saved with status {}, strategy processed", orderId, incomingOrder.getStatus());
        }
    }

    /**
     * Helper method to selectively update fields from incoming order to target existing order.
     * Customize to avoid overwriting fields unnecessarily.
     */
    private void updateOrderFields(Order target, Order source) {
        target.setStatus(source.getStatus());
        target.setExchangeOrderId(source.getExchangeOrderId());
        target.setOrderSide(source.getOrderSide());
        target.setOrderType(source.getOrderType());
        target.setProductType(source.getProductType());
        target.setPrice(source.getPrice());
        target.setQuantity(source.getQuantity());
        target.setExecutionType(source.getExecutionType());
        target.setAverageTradedPrice(source.getAverageTradedPrice());
        target.setLeavesQuantity(source.getLeavesQuantity());
        target.setCumulativeQuantity(source.getCumulativeQuantity());
        target.setDisclosed_uantity(source.getDisclosed_uantity());
        target.setExchangeTransactTime(source.getExchangeTransactTime());
        target.setLastUpdateTime(source.getLastUpdateTime());
        target.setOrderUniqueIdentifier(source.getOrderUniqueIdentifier());
        target.setMessageCode(source.getMessageCode());
        target.setUniqueKey(source.getUniqueKey());
        target.setCancelRejectReason(source.getCancelRejectReason());
        target.setExchangeSegment(source.getExchangeSegment());
        target.setDeployedOn(source.getDeployedOn());
        target.setExchangeInstrumentId(source.getExchangeInstrumentId());
        target.setInstrumentName(source.getInstrumentName());
        target.setTokenID(source.getTokenID());

        // Note: Do NOT overwrite the Signal, Strategy, or AppUser references unintentionally.
    }

    /**
     * Utility to find StrategyLeg for an order; adapt as needed.
     */
    private StrategyLeg findStrategyLeg(Order order) {
        if (order.getSignal() == null)
            return null;

        // Attempt to find the StrategyLeg by matching logic or just return null if managed elsewhere
        // This assumes your existing order has a reference or you can query by order unique key

        String orderUniqueId = order.getOrderUniqueIdentifier();
        if (orderUniqueId == null || orderUniqueId.isEmpty())
            return null;

        String[] parts = orderUniqueId.split("_");
        if (parts.length < 3)
            return null;

        try {
            Long legId = Long.parseLong(parts[2].trim());
            return strategyLegRepository.findById(legId).orElse(null);
        } catch (NumberFormatException ex) {
            logger.warn("Cannot parse legId from orderUniqueIdentifier: {}", orderUniqueId, ex);
            return null;
        }
    }

    private String mapTransactionTypeToOrderSide(String transactionType) {
        if (transactionType == null) return "";
        return transactionType.equalsIgnoreCase("B") ? "BUY" : "SELL";
    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public Order mapToOrder(com.market.proto.tr.OrderStatusFeed orderStatusFeed) {
//        Order order = new Order();
//        StrategyLeg strategyLeg = null;
//        DateTimeFormatter fallbackFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
//
//        try {
//            Optional<Order> existingOrder = orderRepository.getByAppOrderID(orderStatusFeed.getSOrderNumber());
//            if (existingOrder.isPresent()) {
//                order = existingOrder.get();
//            }
////            if ("Traded".equalsIgnoreCase(order.getStatus())) {
////                logger.info("Skipped: Trying to override the order status which is already marked traded");
////                throw new RuntimeException("Trying to override the order status which is already marked traded");
////            }
//            Signal orderSignal = new Signal();
//
//            // Map basic order fields
//            order.setAppOrderID(orderStatusFeed.getSOrderNumber());
//            order.setExchangeOrderId(orderStatusFeed.getSExchOrderNumber());
//            order.setOrderSide(mapTransactionTypeToOrderSide(orderStatusFeed.getCBuyOrSell()));
//            order.setOrderType(orderStatusFeed.getSOrderType());
//            order.setProductType(orderStatusFeed.getSProductName());
//            order.setPrice((long) (orderStatusFeed.getFPrice()*AMOUNT_MULTIPLIER));
//            order.setQuantity((long) orderStatusFeed.getITotalQty());
//            order.setExecutionType(orderStatusFeed.getSOrderType().equalsIgnoreCase("LMT") ? "Limit" : "Market");
//            order.setStatus(orderStatusFeed.getSStatus());
//            order.setAverageTradedPrice(String.valueOf(orderStatusFeed.getFPrice()));
//            order.setLeavesQuantity((long) orderStatusFeed.getITotalQtyRem());
//            order.setCumulativeQuantity((long) (orderStatusFeed.getITotalQty() - orderStatusFeed.getITotalQtyRem()));
//            order.setDisclosed_uantity(0L); // Not available
//            order.setExchangeTransactTime(orderStatusFeed.getSExchOrderTime());
//            order.setLastUpdateTime(orderStatusFeed.getSLastUpdatedTime());
//            order.setOrderUniqueIdentifier(orderStatusFeed.getOrderUniqueIdentifier()); // Using order number as unique identifier
//            order.setMessageCode(0L); // Not available
//            order.setUniqueKey(orderStatusFeed.getSOrderNumber());
//            order.setCancelRejectReason(orderStatusFeed.getSReasonDesc());
//            order.setExchangeSegment(orderStatusFeed.getSExcgId());
//
//            order.setDeployedOn(Optional.ofNullable(orderStatusFeed.getSExchOrderTime())
//                    .filter(s -> !s.isBlank())
//                    .map(s -> {
//                        try {
//                            return LocalDateTime.parse(s).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
//                        } catch (DateTimeParseException e1) {
//                            try {
//                                return LocalDateTime.parse(s, fallbackFormatter).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
//                            } catch (DateTimeParseException e2) {
//                                logger.warn("Unable to parse SExchOrderTime '{}'", s, e2);
//                                return Instant.now();
//                            }
//                        }
//                    })
//                    .orElse(Instant.now()));
//
//            order.setExchangeInstrumentId(Optional.of(orderStatusFeed.getSSecurityId())
//                    .filter(s -> !s.isBlank())
//                    .map(Long::parseLong)
//                    .orElse(0L));
//
//            order.setInstrumentName(orderStatusFeed.getSCustomSym());
//            order.setTokenID(orderStatusFeed.getSSecurityId());
//
//            if (orderStatusFeed.getSStatus().equalsIgnoreCase("Rejected")) {
//                order.setCancelRejectReason(Optional.of(orderStatusFeed.getSReasonDesc())
//                        .filter(s -> !s.isBlank())
//                        .orElse("Order rejected"));
//            }
//
//            // Handle order unique identifier to link with strategy/signal
//            if (orderStatusFeed.getOrderUniqueIdentifier() != null && !orderStatusFeed.getOrderUniqueIdentifier().isEmpty()) {
//                order.setUniqueKey(orderStatusFeed.getOrderUniqueIdentifier());
//                // You may need to adjust this based on how you generate orderUniqueIdentifier
//                String[] parts = orderStatusFeed.getOrderUniqueIdentifier().split("_");
//                if (parts.length >= 3) {
//                    String signalId = parts[1];
//                    String legId = parts[2];
//                    Long l1 = Long.parseLong(signalId.trim());
//                    Long l2 = Long.parseLong(legId.trim());
//
//                    Optional<Signal> orderSignalOptional = signalRepository.findById(l1);
//                    Optional<StrategyLeg> orderLeg = strategyLegRepository.findById(l2);
//
//                    if (orderSignalOptional.isEmpty()) {
//                        throw new SignalNotFoundException("Signal not found for the given signal id: " +
//                                orderStatusFeed.getOrderUniqueIdentifier());
//                    }
//                    if (orderLeg.isEmpty()) {
//                        throw new OrderLegNotFoundException("Strategy leg not found for the given leg id: " +
//                                orderStatusFeed.getOrderUniqueIdentifier());
//                    }
//
//                    orderSignal = orderSignalOptional.get();
//                    strategyLeg = orderLeg.get();
//                    order.setSignal(orderSignal);
//                    order.setAppUser(orderSignal.getAppUser());
//                    order.setStrategy(orderSignal.getStrategy());
//                    order.setUserAdmin(orderSignal.getAppUser().getAdmin());
//                    order.setUnderlying(orderSignal.getStrategy().getUnderlying().getName());
//                    order.setInstrumentName(strategyLeg.getName());
//                    order.setSourceType(OmsType.OMS_TR.getOmsType());
//                }
//            }
//
//            orderCommonService.processStrategyBasedOnOrder(order, strategyLeg, orderSignal);
//            return order;
//
//        } catch (RuntimeException e) {
//            logger.error("Unable to map OrderStatusFeed: " + e.getMessage(), e);
//        } catch (Exception e) {
//            logger.error("Unexpected error mapping OrderStatusFeed", e);
//            throw new RuntimeException(e);
//        }
//        return null;
//    }


    @Transactional
    public void orderFailureHandler(Order order, StrategyLeg strategyLeg) {
        logger.error("Order failed with status: {} and reason: {}", order.getStatus(), order.getCancelRejectReason());
        if (strategyLeg != null) {
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setDescription(new ArrayList<>(List.of(order.getCancelRejectReason())));
            deploymentErrors.setStrategy(strategyLeg.getStrategy());
            deploymentErrors.setAppUser(strategyLeg.getAppUser());
            deploymentErrors.setErrorCode(order.getMessageCode().toString());
            deploymentErrors.setStatus(order.getStatus());
            deploymentErrors.setDeployedOn(order.getDeployedOn());
            deploymentErrors.getDescription().add(order.getCancelRejectReason());

            Strategy strategy = order.getStrategy();
            strategy.setStatus(ApiStatus.ERROR.getKey());
            strategyRepository.save(strategy);

            deploymentErrors.setStrategyLeg(strategyLeg);
            deploymentErrorsRepository.save(deploymentErrors);
            strategyLeg.setStatus(ApiStatus.ERROR.getKey());
        }
    }
}
