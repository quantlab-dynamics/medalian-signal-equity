package com.quantlab.signal.dto;

import lombok.Data;

import java.util.List;
@Data
public class TrPlaceOrderDto {
    private String signalID;
    private String tenantID;
    private String token;
    private String iv = "";
    private Boolean exitFlag;
    private Long requiredCapital;
    private List<TrOrdersDto> orders;
}
