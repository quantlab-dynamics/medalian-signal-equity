package com.quantlab.signal.dto;

import lombok.Data;

import java.util.List;

@Data
public class PlaceOrderDto {
    private String tenantID;
    private String signalID;
    private String appKey;
    private String secretKey;
    private String tokenID;
    private Boolean exitFlag;
    private List<OrdersDto> orders;
}
