package com.quantlab.signal.dto;

import lombok.Data;

@Data
public class TrOrdersDto {
    private String clientId;
    private String userId;
    private String txnType;
    private String userType;
    private String exchange;
    private String segment;
    private String product;
    private String securityId;
    private int quantity;
    private double price;
    private String validity;
    private String orderType;
    private int discQuantity;
    private double triggerPrice;
    private String offMktFlag;
    private String remarks;
    private String mktType;
    private String settlor;
    private String groupId;
    private String remark1;
    private String strategyId;
    private String remark2;
    private String orderUniqueIdentifier;
    private String userAgent;
    private String ipAddress;
}