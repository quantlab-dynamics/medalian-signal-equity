package com.quantlab.common.dto;

import com.quantlab.common.entity.Underlying;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StrategyPNLDto {
    private Long id;
    private String category;
    private String expiry;
    private String underlyingName;
    private String status;
    private Long underlyingId;
}
