
package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MontlyStatisticsDto {
    private String month;
    private int totalTrades;
    private double pnlRs;
    private double pnlPercent;
}
