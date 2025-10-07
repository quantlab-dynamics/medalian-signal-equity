package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.TrOrdersDto;
import com.quantlab.signal.dto.TrPlaceOrderDto;
import com.quantlab.signal.dto.XtsOrdersDto;
import com.quantlab.signal.dto.XtsPlaceOrderDto;
import com.quantlab.signal.grpcserver.OrderPlaceGrpc;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service
public class GrpcService {
    private static final Logger logger = LoggerFactory.getLogger(GrpcService.class);

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    OrderPlaceGrpc orderPlaceGrpc;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    private AppUserLogInfoRepository appUserLogInfoRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    GrpcErrorService grpcErrorService;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSignal(Signal signal) {
        logger.info("Initiating sendSignal process for signal ID: {}", signal.getId());
        Hibernate.initialize(signal.getAppUser());
        UserAuthConstants userAuthConstants = userAuthConstantsRepository.findByAppUserUserId(signal.getAppUser().getUserId());

        if (userAuthConstants.getXtsClient()) {
            sendXtsSignal(signal, userAuthConstants);
        } else {
            sendTrSignal(signal, userAuthConstants);
        }
    }

    private void sendXtsSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            XtsPlaceOrderDto xtsPlaceOrderDto = createXtsPlaceOrderDto(signal, userAuthConstants);
            List<XtsOrdersDto> xtsOrders = createXtsOrders(signal, userAuthConstants);
            xtsPlaceOrderDto.setOrders(xtsOrders);

            com.market.proto.xts.PlaceOrderResponse res = orderPlaceGrpc.placeOrder(xtsPlaceOrderDto);
            logger.info("XTS Place order response received for signal ID: {}, Response: {}", signal.getId(), res);
            grpcErrorService.processGrpcResponse(res, signal);
        } catch (Exception e) {
            handleSignalError(e, signal, "XTS");
        }
    }

    private void sendTrSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            TrPlaceOrderDto trPlaceOrderDto = createTrPlaceOrderDto(signal, userAuthConstants);
            List<TrOrdersDto> trOrders = createTrOrders(signal, userAuthConstants);
            trPlaceOrderDto.setOrders(trOrders);

            com.market.proto.tr.PlaceOrderResponse res = orderPlaceGrpc.placeOrder(trPlaceOrderDto);
            logger.info("TR Place order response received for signal ID: {}, strategyID: {} , Response: {}", signal.getId(),signal.getStrategy().getId(), res);
            grpcErrorService.processGrpcResponse(res, signal, trOrders);
        } catch (Exception e) {
            handleSignalError(e, signal, "TR");
        }
    }

    private XtsPlaceOrderDto createXtsPlaceOrderDto(Signal signal, UserAuthConstants userAuthConstants) {
        XtsPlaceOrderDto dto = new XtsPlaceOrderDto();
        dto.setSignalID(signal.getId().toString());
        dto.setToken(userAuthConstants.getXtsToken());
        dto.setExitFlag(false);
        dto.setAppKey(userAuthConstants.getXtsAppKey());
        dto.setSecretKey(userAuthConstants.getXtsSecretKey());
        dto.setTenantID(userAuthConstants.getClientId());
        return dto;
    }

    private TrPlaceOrderDto createTrPlaceOrderDto(Signal signal, UserAuthConstants userAuthConstants) {
        TrPlaceOrderDto dto = new TrPlaceOrderDto();
        dto.setSignalID(signal.getId().toString());
        dto.setTenantID(userAuthConstants.getClientId());
        dto.setToken(userAuthConstants.getToken());
        dto.setIv(""); // Set appropriate IV
        if (isNotAdjustmentOrder(signal))
            dto.setRequiredCapital((signal.getStrategy().getMinCapital() / AMOUNT_MULTIPLIER) * signal.getStrategy().getMultiplier());
        dto.setExitFlag(false);
        return dto;
    }

    private boolean isNotAdjustmentOrder(Signal signal) {
        for (StrategyLeg leg : signal.getSignalLegs()) {
            if (!(leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey())
                    || leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.ERROR_PLACING_ORDER.getKey())
                    || leg.getStatus().equalsIgnoreCase(LegStatus.TYPE_OPEN.getKey())
                    || leg.getStatus().equalsIgnoreCase(Status.ERROR.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private List<XtsOrdersDto> createXtsOrders(Signal signal, UserAuthConstants userAuthConstants) {
        Hibernate.initialize(signal.getStrategy());
        Strategy strategy = signal.getStrategy();

        return signal.getSignalLegs().stream().filter(dao->dao.getExchangeStatus().equalsIgnoreCase(LegStatus.EXCHANGE.getKey())).map(dto -> {

                XtsOrdersDto order = new XtsOrdersDto();
                order.setClientID(userAuthConstants.getClientId());
                order.setUserID(userAuthConstants.getClientId());
                order.setExchangeSegment(dto.getSegment());
                order.setExchangeInstrumentId(dto.getExchangeInstrumentId().intValue());
                order.setOrderUniqueIdentifier("QO_" + signal.getId() + "_" + dto.getId().toString());
                order.setOrderType("LIMIT");
                order.setOrderSide(dto.getBuySellFlag().toUpperCase());
                order.setTimeInForce("DAY");
                int quantity = (int) (dto.getLotSize() * dto.getNoOfLots());
                order.setOrderQuantity(quantity);
                order.setNoLots(Math.toIntExact(dto.getNoOfLots()));
                order.setLotSize(dto.getLotSize().intValue());
                order.setMultiply(strategy.getMultiplier().intValue());
                order.setProductType(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "MIS" : "NRML");
                order.setLimitPrice(dto.getPrice());
                order.setAlgoID(strategy.getAlgoId());
                order.setAlgoCategory(strategy.getAlgoCategory());

                return order;
        }).toList();
    }

    private List<TrOrdersDto> createTrOrders(Signal signal, UserAuthConstants userAuthConstants) {
        Hibernate.initialize(signal.getStrategy());
        Strategy strategy = signal.getStrategy();

        Optional<AppUserLogInfo> latestLogInfoOpt = appUserLogInfoRepository.findTopByAppUserOrderByLoggedinTimeDesc(userAuthConstants.getAppUser());

        return signal.getSignalLegs().stream().filter(dao->((dao.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey())))
                && dao.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())).map(dto -> {
                    TrOrdersDto order = new TrOrdersDto();

                    // Set basic fields from documentation
                    order.setClientId(userAuthConstants.getClientId());
                    order.setUserId(userAuthConstants.getClientId());
                    order.setTxnType("BUY".equalsIgnoreCase(dto.getBuySellFlag()) ? "B" : "S");
                    order.setSecurityId(dto.getExchangeInstrumentId().toString());
                    order.setQuantity((int) (dto.getLotSize() * dto.getNoOfLots()));
                    order.setPrice(dto.getPrice());
                    order.setValidity(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "DAY" : "NRML");
                    order.setProduct(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "I" : "H");
                    order.setOrderType("LMT");
                    order.setDiscQuantity(0);
                    order.setTriggerPrice(0);
                    order.setOffMktFlag("false");

                    latestLogInfoOpt.ifPresent(logInfo -> {
                        order.setUserAgent(logInfo.getUserAgent());
                        order.setIpAddress(logInfo.getMechineId());
                    });

                    String uniqueIdentifier = "QO_" + signal.getId() + "_" + dto.getId().toString();

                    // Replace underscores with "S" because API is not accepting special characters
                    order.setRemarks(uniqueIdentifier.replace("_", "S"));
                    order.setOrderUniqueIdentifier(uniqueIdentifier);
                    order.setRemark1("1");
                    order.setRemark2("R2new");

                    String exchange = dto.getSegment();
                    if (exchange != null) {
                        if (exchange.startsWith("NSE")) {
                            order.setExchange("NSE");
                            order.setSegment("D");
//                            order.setProduct("M");
                        } else if (exchange.startsWith("BSE")) {
                            order.setExchange("BSE");
                            order.setSegment("D");
//                            order.setProduct("M");
                        }
                    }

                    if (strategy.getAlgoId() != null) {
                        order.setStrategyId(strategy.getAlgoId());
                    }

                    return order;
                }).toList();
    }


    private void handleSignalError(Exception e, Signal signal, String protocol) {
        String errorMessage = e.getMessage() != null ? e.getMessage() : RUN_TIME_EXCEPTION + ": error while Sending " + protocol + " Signal";
        logger.error("Error while sending {} Signal: {}", protocol, e.getMessage());
        logger.error("Error in signal {}: {}", signal.getId(), errorMessage);
        grpcErrorService.placingOrderLogs(ERROR_PLACING_ORDER_DESCRIPTION, signal, Status.ERROR.getKey());
    }

    @Async
    public void sendExitSignal(Signal signal) {
        logger.info("Initiating sendExitSignal process for signal ID: {}", signal.getId());
        Hibernate.initialize(signal.getAppUser());
        UserAuthConstants userAuthConstants = userAuthConstantsRepository.findByAppUserUserId(signal.getAppUser().getUserId());

        if (userAuthConstants.getXtsClient()) {
            sendXtsExitSignal(signal, userAuthConstants);
        } else {
            sendTrExitSignal(signal, userAuthConstants);
        }
        updateSignalLegs(signal);
    }

    @Transactional
    public void updateSignalLegs(Signal signal) {
        signal.getSignalLegs().stream()
                .filter(leg -> Status.PENDING.getKey().equalsIgnoreCase(leg.getStatus()))
                .forEach(leg -> {
                    leg.setStatus(Status.PENDING.getKey());
                    strategyLegRepository.save(leg);
                });

    }

    private void sendXtsExitSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            XtsPlaceOrderDto xtsPlaceOrderDto = createXtsExitOrderDto(signal, userAuthConstants);
            List<XtsOrdersDto> xtsOrders = createXtsExitOrders(signal, userAuthConstants);
            xtsPlaceOrderDto.setOrders(xtsOrders);

            com.market.proto.xts.PlaceOrderResponse res = orderPlaceGrpc.placeOrder(xtsPlaceOrderDto);
            logger.info("XTS Exit order response received for signal ID: {}, Response: {}", signal.getId(), res);
            grpcErrorService.processGrpcResponse(res, signal);
        } catch (Exception e) {
            handleSignalError(e, signal, "XTS Exit");
        }
    }

    private void sendTrExitSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            TrPlaceOrderDto trPlaceOrderDto = createTrExitOrderDto(signal, userAuthConstants);
            List<TrOrdersDto> trOrders = createTrExitOrders(signal, userAuthConstants);
            trPlaceOrderDto.setOrders(trOrders);

            com.market.proto.tr.PlaceOrderResponse res = orderPlaceGrpc.placeOrder(trPlaceOrderDto);
            logger.info("TR Exit order response received for signal ID: {}, Response: {}", signal.getId(), res);
            grpcErrorService.processGrpcResponse(res, signal, trOrders);
        } catch (Exception e) {
            handleSignalError(e, signal, "TR Exit");
        }
    }

    private XtsPlaceOrderDto createXtsExitOrderDto(Signal signal, UserAuthConstants userAuthConstants) {
        XtsPlaceOrderDto dto = new XtsPlaceOrderDto();
        dto.setSignalID(signal.getId().toString());
        dto.setToken(userAuthConstants.getXtsToken());
        dto.setExitFlag(true);
        dto.setAppKey(userAuthConstants.getXtsAppKey());
        dto.setSecretKey(userAuthConstants.getXtsSecretKey());
        dto.setTenantID(userAuthConstants.getClientId());
        return dto;
    }

    private TrPlaceOrderDto createTrExitOrderDto(Signal signal, UserAuthConstants userAuthConstants) {
        TrPlaceOrderDto dto = new TrPlaceOrderDto();
        dto.setSignalID(signal.getId().toString());
        dto.setTenantID(userAuthConstants.getClientId());
        dto.setToken(userAuthConstants.getToken());
        dto.setIv(""); // Set appropriate IV
        dto.setExitFlag(true);
        return dto;
    }

    private List<XtsOrdersDto> createXtsExitOrders(Signal signal, UserAuthConstants userAuthConstants) {
        return signal.getStrategyLeg().stream()
                .filter(dto -> dto.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey()))
                .map(dto -> {
                    XtsOrdersDto order = new XtsOrdersDto();
                    String buySellFlag = dto.getBuySellFlag().equalsIgnoreCase(OrderTypeMenu.BUY.getKey()) ? OrderTypeMenu.BUY.getKey().toUpperCase() : OrderTypeMenu.SELL.getKey().toUpperCase();

                    order.setClientID(userAuthConstants.getClientId());
                    order.setUserID(signal.getAppUser().getUserId());
                    order.setExchangeSegment(dto.getSegment());
                    order.setExchangeInstrumentId(dto.getExchangeInstrumentId().intValue());
                    order.setOrderUniqueIdentifier("QO_"+signal.getId()+"_"+dto.getId().toString());
                    order.setOrderType("LIMIT");
                    order.setOrderSide(buySellFlag);
                    order.setTimeInForce("DAY");

                    int quantity = (int) (dto.getLotSize()*dto.getNoOfLots());
                    order.setOrderQuantity(quantity);
                    order.setNoLots(Math.toIntExact(dto.getNoOfLots()));
                    order.setLotSize(dto.getLotSize().intValue());
                    order.setMultiply(signal.getMultiplier().intValue());
                    order.setProductType(dto.getOptionType().equalsIgnoreCase("INTRADAY") ? "MIS" : "NRML");
                    order.setLimitPrice(dto.getPrice());

                    return order;
                })
                .toList();
    }

    private List<TrOrdersDto> createTrExitOrders(Signal signal, UserAuthConstants userAuthConstants) {
        Hibernate.initialize(signal.getStrategy());
        Strategy strategy = signal.getStrategy();
        Optional<AppUserLogInfo> latestLogInfoOpt = appUserLogInfoRepository.findTopByAppUserOrderByLoggedinTimeDesc(userAuthConstants.getAppUser());

        return signal.getSignalLegs().stream()
                .filter(dto -> dto.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey()) && dto.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey()))
                .map(dto -> {

                    TrOrdersDto order = new TrOrdersDto();

                    // Set basic fields from documentation
                    order.setClientId(userAuthConstants.getClientId());
                    order.setUserId(userAuthConstants.getClientId());
                    order.setTxnType("BUY".equalsIgnoreCase(dto.getBuySellFlag()) ? "S" : "B");
                    order.setSecurityId(dto.getExchangeInstrumentId().toString());
                    order.setQuantity((int) (dto.getLotSize() * dto.getNoOfLots()));
                    order.setPrice(dto.getPrice());
                    order.setValidity(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "DAY" : "NRML");
                    order.setProduct(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "I" : "H");
                    order.setOrderType("LMT");
                    order.setDiscQuantity(0);
                    order.setTriggerPrice(0);
                    order.setOffMktFlag("false");
                    latestLogInfoOpt.ifPresent(logInfo -> {
                        order.setUserAgent(logInfo.getUserAgent());
                        order.setIpAddress(logInfo.getMechineId());
                    });

                    String uniqueIdentifier = "QO_" + signal.getId() + "_" + dto.getId().toString();

                    // Replace underscores with "S" because API is not accepting special characters
                    order.setRemarks(uniqueIdentifier.replace("_", "S"));
                    order.setOrderUniqueIdentifier(uniqueIdentifier);
                    order.setRemark1("1");
                    order.setRemark2("R2new");

                    String exchange = dto.getSegment();
                    if (exchange != null) {
                        if (exchange.startsWith("NSE")) {
                            order.setExchange("NSE");
                            order.setSegment("D");
//                            order.setProduct("M");
                        } else if (exchange.startsWith("BSE")) {
                            order.setExchange("BSE");
                            order.setSegment("D");
//                            order.setProduct("M");
                        }
                    }

                    if (strategy.getAlgoId() != null) {
                        order.setStrategyId(strategy.getAlgoId());
                    }

                    return order;
                }).toList();

    }
}
