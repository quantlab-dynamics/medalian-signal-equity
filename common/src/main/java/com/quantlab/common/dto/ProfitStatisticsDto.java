package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfitStatisticsDto {
    private Long totalProfit;
    private Long monthlyAvg;
    public Long totalROI;
}
