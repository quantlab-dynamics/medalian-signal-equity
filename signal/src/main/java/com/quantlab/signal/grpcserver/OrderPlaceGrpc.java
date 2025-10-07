package com.quantlab.signal.grpcserver;


import com.google.protobuf.BoolValue;
import com.market.proto.tr.PlaceOrder;
import com.quantlab.common.entity.Order;
import com.quantlab.common.repository.OrderRepository;
import com.quantlab.signal.dto.TrOrdersDto;
import com.quantlab.signal.dto.TrPlaceOrderDto;
import com.quantlab.signal.dto.XtsOrdersDto;
import com.quantlab.signal.dto.XtsPlaceOrderDto;
import com.quantlab.signal.service.OrderPlacedResponseMapper;
import com.quantlab.signal.service.OrderStatusFeedMapper;
import com.quantlab.signal.service.redisService.TouchLineService;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.quantlab.common.utils.staticstore.AppConstants.ALLOWED_TENANT_IDS;
import static com.quantlab.signal.utils.StrategyConstants.DEFAULT_AMOUNT_INTERVAL;


@Service
public class OrderPlaceGrpc {
    private static final Logger logger = LogManager.getLogger(OrderPlaceGrpc.class);

    @Autowired private ModelMapper modelMapper;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderPlacedResponseMapper orderPlacedResponseMapper;
    @Autowired private OrderStatusFeedMapper orderStatusFeedMapper;
    @Autowired private TouchLineService touchLineService;

    // gRPC clients injected by Spring
    @GrpcClient("grpc-quantlab-service-xts")
    private com.market.proto.xts.PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub xtsSynchronousClient;

    @GrpcClient("grpc-quantlab-service-xts")
    private com.market.proto.xts.OrderBookStreamServiceGrpc.OrderBookStreamServiceStub xtsAsyncStub;

    @GrpcClient("grpc-quantlab-service-tr")
    private com.market.proto.tr.PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub trSynchronousClient;

    @GrpcClient("grpc-quantlab-service-tr")
    private com.market.proto.tr.OrderStatusFeedStreamServiceGrpc.OrderStatusFeedStreamServiceStub trAsyncStub;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isTrStreamActive = new AtomicBoolean(false);

    @PostConstruct
    public void initStreams() {
        startStreamingOrdersPlaced();
    }

    public com.market.proto.xts.PlaceOrderResponse placeOrder(XtsPlaceOrderDto request) {
        return placeXtsOrder(request);
    }
//"T9193259".equalsIgnoreCase(request.getTenantID()) ||  || "T7262742".equalsIgnoreCase(request.getTenantID())
// "T0118221" Saurabh Gandhi
//     || "T5466324".equalsIgnoreCase(request.getTenantID())  satyam
    //  T5886325 Marketing Team
    public com.market.proto.tr.PlaceOrderResponse placeOrder(TrPlaceOrderDto request) {
        if (ALLOWED_TENANT_IDS.contains(request.getTenantID().toUpperCase())) {
            return placeTrOrder(request);
        } else throw new RuntimeException("User Not enabled for Live Trading");
    }

    private com.market.proto.xts.PlaceOrderResponse placeXtsOrder(XtsPlaceOrderDto request) {
        logger.info("Placing XTS order for signal ID: {}", request.getSignalID());
        com.market.proto.xts.PlaceOrderRequest placeRequest = com.market.proto.xts.PlaceOrderRequest.newBuilder()
                .setSignalID(request.getSignalID())
                .setTenantID(request.getTenantID())
                .setAppKey(request.getAppKey())
                .setSecretKey(request.getSecretKey())
                .setToken(request.getToken())
                .setExitFlag(BoolValue.of(request.getExitFlag()))
                .addAllOrders(mapXtsOrders(request.getOrders()))
                .build();

        try {
            return xtsSynchronousClient.placeOrder(placeRequest);
        } catch (Exception e) {
            logger.error("XTS order placement failed for signal ID: {}", request.getSignalID(), e);
            return null;
        }
    }

    private com.market.proto.tr.PlaceOrderResponse placeTrOrder(TrPlaceOrderDto request) {
        logger.info("Placing TR order for signal ID: {}", request.getSignalID());
        logger.info("Incoming TrPlaceOrderDto request: {}", request);
        com.market.proto.tr.PlaceOrderRequest.Builder builder = com.market.proto.tr.PlaceOrderRequest.newBuilder();

        if (request.getSignalID() != null) {
            builder.setSignalID(request.getSignalID());
        }

        if (request.getTenantID() != null) {
            builder.setTenantID(request.getTenantID());
        }

        if (request.getToken() != null) {
            builder.setToken(request.getToken());
        }


        if (request.getExitFlag() != null) {
            builder.setExitFlag(BoolValue.of(request.getExitFlag()));
        }

        if (request.getIv() != null) {
            builder.setIv(request.getIv());
        }

        if (request.getOrders() != null) {
            builder.addAllOrders(mapTrOrders(request.getOrders()));
        }
        if (request.getRequiredCapital() !=null){
            builder.setRequiredCapital(Math.toIntExact(request.getRequiredCapital()));
        }

        com.market.proto.tr.PlaceOrderRequest trRequest = builder.build();

        logger.info("Sending gRPC PlaceOrderRequest: {}", trRequest);
//        "T9193259".equalsIgnoreCase(request.getOrders().get(0).getClientId()) || || "T7262742".equalsIgnoreCase(request.getTenantID())
        // "T0118221" Saurabh Gandhi
//          || "T5466324".equalsIgnoreCase(request.getTenantID())
        try {
            if (ALLOWED_TENANT_IDS.contains(request.getTenantID())) {
                com.market.proto.tr.PlaceOrderResponse response = trSynchronousClient.placeOrder(trRequest);
                logger.info("Received gRPC PlaceOrderResponse: {}", response);
                return response;
            } else throw new RuntimeException("User Not enabled for Live Trading");
        } catch (Exception e) {
            logger.error("TR order placement failed for signal ID: {}", request.getSignalID(), e);
            // Log that the response is null due to an exception
            logger.debug("TR order placement failed, returning null response for signal ID: {}", request.getSignalID());
            return null;
        }
    }


    private List<com.market.proto.xts.PlaceOrder> mapXtsOrders(List<XtsOrdersDto> orders) {
        return orders.stream().map(dto -> {
            com.market.proto.xts.PlaceOrder.Builder builder = com.market.proto.xts.PlaceOrder.newBuilder()
                    .setExchangeSegment(dto.getExchangeSegment())
                    .setExchangeInstrumentId(dto.getExchangeInstrumentId())
                    .setOrderType(dto.getOrderType())
                    .setClientID(dto.getClientID())
                    .setUserID(dto.getUserID())
                    .setOrderSide(dto.getOrderSide())
                    .setTimeInForce(dto.getTimeInForce())
                    .setLimitPrice(dto.getLimitPrice())
                    .setOrderUniqueIdentifier(dto.getOrderUniqueIdentifier())
                    .setProductType(dto.getProductType())
                    .setNoLots(dto.getNoLots())
                    .setLotSize(dto.getLotSize())
                    .setMultiply(dto.getMultiply() < 0 ? 1 : dto.getMultiply());

            if (dto.getAlgoID() != null) builder.setAlgoID(dto.getAlgoID());
            if (dto.getAlgoCategory() != null) builder.setAlgoCategory(dto.getAlgoCategory());

            // UAT specific adjustment (remove for production)
            long limitPriceForUatOnly = (long) (dto.getLimitPrice()/DEFAULT_AMOUNT_INTERVAL);
            builder.setLimitPrice(limitPriceForUatOnly);

            return builder.build();
        }).toList();
    }

    private List<com.market.proto.tr.PlaceOrder> mapTrOrders(List<TrOrdersDto> orders) {
        try {

            return orders.stream().map(orderDto -> {
                logger.info("order socket processing data to map : {}",orderDto);
                return PlaceOrder.newBuilder()
                        .setClientId(orderDto.getClientId())
                        .setUserId(orderDto.getUserId())
                        .setTxnType(orderDto.getTxnType())
                        .setExchange(orderDto.getExchange())
                        .setSegment(orderDto.getSegment())
                        .setProduct(orderDto.getProduct())
                        .setSecurityId(orderDto.getSecurityId())
                        .setQuantity(orderDto.getQuantity())
                        .setPrice(orderDto.getPrice())
                        .setValidity(orderDto.getValidity())
                        .setOrderType(orderDto.getOrderType())
                        .setDiscQuantity(orderDto.getDiscQuantity())
                        .setTriggerPrice(orderDto.getTriggerPrice())
                        .setOffMktFlag(orderDto.getOffMktFlag())
                        .setClientIpAddress(orderDto.getIpAddress() != null ? orderDto.getIpAddress() : "")
                        .setUserAgent(orderDto.getUserAgent() != null ? orderDto.getUserAgent() : "")
                        .setRemarks(orderDto.getRemarks() != null ? orderDto.getRemarks() : "")
                        .setMktType(orderDto.getMktType() != null ? orderDto.getMktType() : "")
                        .setSettlor(orderDto.getSettlor() != null ? orderDto.getSettlor() : "")
                        .setGroupId(orderDto.getGroupId() != null ? orderDto.getGroupId() : "")
                        .setRemark1(orderDto.getRemark1() != null ? orderDto.getRemark1() : "")
                        .setStrategyId(orderDto.getStrategyId() != null ? orderDto.getStrategyId() : "")
                        .setRemark2(orderDto.getRemark2() != null ? orderDto.getRemark2() : "")
                        .setOrderUniqueIdentifier(orderDto.getOrderUniqueIdentifier())
                        .build();
            }).toList();
        }catch (Exception e){
            logger.error("Error in mapping TR orders: ", e);
        }
        return null;
    }

    public void startStreamingOrdersPlaced() {
//        streamXtsOrderPlaceData();
        streamTrOrderPlaceData();
    }


    public void streamXtsOrderPlaceData() {
        com.market.proto.xts.OrderBookStreamRequest request = com.market.proto.xts.OrderBookStreamRequest.newBuilder().build();
        logger.info("Starting XTS streamOrderPlaceData...");
        try {
            xtsAsyncStub.streamOrderBookData(request, new StreamObserver<com.market.proto.xts.OrderBookResponseStream>() {
                @Override
                public void onNext(com.market.proto.xts.OrderBookResponseStream orderBookResponseStream) {
                    Order placedOrderData = orderPlacedResponseMapper.mapToOrder(orderBookResponseStream);
                    storeOrderEntity(placedOrderData);
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Error during XTS streaming: " + throwable.getMessage());
//                    retryXtsOrderStream();
                }

                @Override
                public void onCompleted() {
                    logger.info("XTS streaming completed.");
//                    retryXtsOrderStream();
                }
            });
        } catch (Exception e) {
            logger.error("Exception in XTS streaming", e);
        }
    }


    public void streamTrOrderPlaceData() {
        if (isTrStreamActive.get()) {
            logger.warn("TR stream already active. Skipping duplicate connection.");
            return;
        }

        isTrStreamActive.set(true);
        com.market.proto.tr.OrderStatusFeedStreamRequest request =
                com.market.proto.tr.OrderStatusFeedStreamRequest.newBuilder().build();

        logger.info("Starting TR streamOrderPlaceData...");
        try {
            trAsyncStub.streamOrderStatusFeed(request, new StreamObserver<com.market.proto.tr.OrderStatusFeed>() {
                @Override
                public void onNext(com.market.proto.tr.OrderStatusFeed orderStatusFeed) {
                    logger.info("Received TR order status feed: " + orderStatusFeed);
                    try {
                        Order incomingOrder = orderStatusFeedMapper.mapToOrder(orderStatusFeed);
                        if (incomingOrder != null) {
                            orderStatusFeedMapper.saveOrUpdateOrderWithPrecedenceAndProcessStrategy(incomingOrder);
                        }
                    } catch (Exception e) {
                        logger.error("Error processing TR order status feed :  {}", e.getMessage(), e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    isTrStreamActive.set(false);
                    logger.error("Error during TR streaming: " + throwable.getMessage());
                    retryTrOrderStream();
                }

                @Override
                public void onCompleted() {
                    isTrStreamActive.set(false);
                    logger.info("TR streaming completed.");
//                    retryTrOrderStream();
                }
            });
        } catch (Exception e) {
            isTrStreamActive.set(false);
            logger.error("Exception in TR streaming", e.getMessage());
            retryTrOrderStream();
        }
    }

    private void retryXtsOrderStream() {
        scheduler.schedule(this::streamXtsOrderPlaceData, 2, TimeUnit.SECONDS);
    }

    private void retryTrOrderStream() {
        scheduler.schedule(() -> {
            logger.info("Retrying TR stream... ");
            streamTrOrderPlaceData();
        }, 5, TimeUnit.SECONDS);
    }

    private void storeOrderEntity(Order placedOrderData) {
        try {
            orderRepository.save(placedOrderData);
        } catch (RuntimeException e) {
            logger.error("Unable to store the order details: ", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
