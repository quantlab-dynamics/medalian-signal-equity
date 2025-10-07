
package com.quantlab.client.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsResponseDto<T> {
    List<StatisticsResponseObjDto<T>> performanceOverview;
    List<StatisticsResponseObjDto<T>> ProfitStatistics;
    List<StatisticsResponseObjDto<T>> RiskMetrics;
    List<StatisticsResponseObjDto<T>> statistics;
    List<MontlyStatisticsDto> monthlyStatistics;
    List<WeekStatsSummaryDto> weekStatsSummary;
}
