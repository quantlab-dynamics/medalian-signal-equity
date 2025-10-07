package com.quantlab.common.dto;

import lombok.Data;

public interface ProfitLossStats {
    Long getUserId();
    Long getMinProfit();
    Long getMaxLoss();
    Long getTotalProfitLoss();
    String getClientId();
    Long getLiveStrategiesCount();
}
