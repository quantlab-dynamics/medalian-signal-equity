package com.quantlab.signal.dto.redisDto;

import lombok.Data;

@Data
public class MarginResponseDTO {
    private double utilizedMargin;
    private double availableMargin;
    private double totalBalance;
}
