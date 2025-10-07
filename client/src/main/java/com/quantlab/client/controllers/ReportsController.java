package com.quantlab.client.controllers;

import com.quantlab.client.dto.AllReportsRequestDto;
import com.quantlab.client.dto.AllReportsResponseDto;
import com.quantlab.client.dto.ReportsByStrategyRequestDto;
import com.quantlab.client.dto.ReportsByStrategyResponseDto;
import com.quantlab.client.service.ReportsService;
import com.quantlab.common.common.ApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
public class ReportsController {

    private static final Logger logger = LogManager.getLogger(ReportsController.class);

    @Autowired
    ReportsService reportsService;

    @PostMapping("/all")
    public ResponseEntity<ApiResponse<AllReportsResponseDto>> getAllReports(@RequestHeader String clientId, @RequestBody(required = false) AllReportsRequestDto allReportsRequestDto) {
        try {
            if (allReportsRequestDto == null) {
                allReportsRequestDto = new AllReportsRequestDto();
            }

            AllReportsResponseDto res = reportsService.getAllReports(clientId, allReportsRequestDto);
            return ResponseEntity.ok(new ApiResponse<>(
                    "Reports retrieved successfully",
                    res
            ));
        } catch (Exception e) {
            logger.error("Error getting all reports{}", String.valueOf(e));
            throw new RuntimeException("Error getting all reports");
        }
    }

    @PostMapping("/strategy")
    public ResponseEntity<ApiResponse<ReportsByStrategyResponseDto>> getReportsByStrategy(@RequestHeader String clientId, @RequestBody(required = false) ReportsByStrategyRequestDto reportsByStrategyRequestDto) {
        try {
            if (reportsByStrategyRequestDto == null) {
                reportsByStrategyRequestDto = new ReportsByStrategyRequestDto();
            }

            ReportsByStrategyResponseDto res = reportsService.getReportsByStrategy1(clientId, reportsByStrategyRequestDto);
            return ResponseEntity.ok(new ApiResponse<>(
                    "Reports retrieved successfully",
                    res
            ));
        } catch (Exception e) {
            throw new RuntimeException("Error getting reports by strategyid : " + reportsByStrategyRequestDto.getStrategyId());
        }
    }

    @PostMapping("/strategyreports")
    public ResponseEntity<ApiResponse<ReportsByStrategyResponseDto>> getReportsForStrategy(@RequestHeader String clientId, @RequestBody(required = false) ReportsByStrategyRequestDto reportsByStrategyRequestDto) {
        try {
            if (reportsByStrategyRequestDto == null) {
                reportsByStrategyRequestDto = new ReportsByStrategyRequestDto();
            }

            ReportsByStrategyResponseDto res = reportsService.getReportsByStrategy(clientId, reportsByStrategyRequestDto);
            return ResponseEntity.ok(new ApiResponse<>(
                    "Reports retrieved successfully",
                    res
            ));
        } catch (Exception e) {
            throw new RuntimeException("Error getting reports by strategyid : " + reportsByStrategyRequestDto.getStrategyId());
        }
    }
}
