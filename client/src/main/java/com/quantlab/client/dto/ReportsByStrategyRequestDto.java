package com.quantlab.client.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class ReportsByStrategyRequestDto {

    private Instant fromDate;

    private Instant toDate;

    private Long strategyId;

    private Long brokerId;

}