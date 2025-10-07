package com.quantlab.client.service;

import com.quantlab.client.dto.*;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.exception.custom.StrategyNotFoundException;
import com.quantlab.common.exception.custom.UnauthorizedAccessException;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.LegType;
import com.quantlab.signal.service.AuthService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;
import static com.quantlab.signal.utils.staticdata.StaticStore.roundToTwoDecimalPlaces;

@Service
public class ReportsService {

    private static final Logger logger = LogManager.getLogger(ReportsService.class);

    @Autowired
    EntityManager entityManager;

    @Autowired
    private AuthService authService;

    @Autowired
    private StrategyRepository strategyRepository;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    ZoneId zoneId = ZoneId.systemDefault();

    // Custom query to fetch orders based on date range, strategy ID, and user ID
    public List<Signal> getSignalsByDateRangeAndStrategyIdAndUser(Instant fromDate, Instant toDate, Long strategyId, Long userId) {
        try {
            // Start the base query
            StringBuilder jpql = new StringBuilder("SELECT s FROM Signal s WHERE s.appUser.id = :userId");

            // Conditionally add filters based on provided parameters
            if (fromDate != null && toDate != null) {
                jpql.append(" AND s.createdAt BETWEEN :fromDate AND :toDate");
            } else if (fromDate != null) {
                jpql.append(" AND s.createdAt >= :fromDate");
            } else if (toDate != null) {
                jpql.append(" AND s.createdAt <= :toDate");
            }

            if (strategyId != null) {
                jpql.append(" AND s.strategy.id = :strategyId");
            }

            jpql.append(" ORDER BY s.strategy.id ASC, s.createdAt DESC");

            // Create the query
            TypedQuery<Signal> query = entityManager.createQuery(jpql.toString(), Signal.class);

            // Set the required user ID parameter
            query.setParameter("userId", userId);

            // Set parameters dynamically based on provided arguments
            if (fromDate != null && toDate != null) {
                query.setParameter("fromDate", fromDate);
                query.setParameter("toDate", toDate);
            }

            if (strategyId != null) {
                query.setParameter("strategyId", strategyId);
            }

            return query.getResultList();
        } catch (Exception e) {
            // Catch all other unexpected exceptions
            System.err.println("An unexpected error occurred: " + e.getMessage());
            throw new RuntimeException("An unexpected error occurred, please try again later.");
        }
    }

    public AllReportsResponseDto getAllReports(String clientId, AllReportsRequestDto allReportsRequestDto) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);

            List<Signal> signalList = getSignalsByDateRangeAndStrategyIdAndUser(
                    allReportsRequestDto.getFromDate(),
                    allReportsRequestDto.getToDate(),
                    allReportsRequestDto.getStrategyId(),
                    appUser.getId()
            );

            AllReportsResponseDto res = new AllReportsResponseDto();
            long totalPnl = 0L;
            List<AllReportsSingleResponseDto> allReportsSingleResponseDtoList = new ArrayList<>();
            Map<String, AllReportsSingleResponseDto> map = new LinkedHashMap<>();

            for (Signal signal : signalList) {
                String strategyName = signal.getStrategy().getName();
                Long strategyId = signal.getStrategy().getId();
                if (map.containsKey(strategyName)) {
                    map.get(strategyName).setPnl(map.get(strategyName).getPnl() + (signal.getProfitLoss()==null ? 0:signal.getProfitLoss()/(double)AMOUNT_MULTIPLIER));
                } else {
                    map.put(strategyName, new AllReportsSingleResponseDto(strategyId, strategyName, signal.getProfitLoss()));
                }
            }

            for (AllReportsSingleResponseDto item : map.values()) {
                totalPnl += item.getPnl();
                allReportsSingleResponseDtoList.add(item);
            }

            res.setReports(allReportsSingleResponseDtoList);
            res.setTotalPnl(totalPnl/(double)AMOUNT_MULTIPLIER);
            return res;
        } catch (Exception e) {
            logger.error("Error fetching reports : {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching reports", e);
        }
    }

    public ReportsByStrategyResponseDto getReportsByStrategy1(String clientId, ReportsByStrategyRequestDto requestDto) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(requestDto.getStrategyId())
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + requestDto.getStrategyId()));

            if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
                throw new UnauthorizedAccessException("Cannot modify other user's strategy");
            }

            List<Signal> signalList = getSignalsByDateRangeAndStrategyIdAndUser(
                    requestDto.getFromDate(),
                    requestDto.getToDate(),
                    requestDto.getStrategyId(),
                    appUser.getId()
            );

            Map<String, ReportsSignalDto> map = new HashMap<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

            for (Signal signal : signalList) {
                String formattedDate = signal.getCreatedAt()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(formatter);
                if (map.containsKey(formattedDate)) {
                    map.get(formattedDate).setPnl(map.get(formattedDate).getPnl() + signal.getProfitLoss());
                } else {
                    ReportsSignalDto signalDto = new ReportsSignalDto();
//                    signalDto.setStrategyName(signal.getStrategy().getName());
                    signalDto.setDate(formattedDate);
                    signalDto.setPnl(signal.getProfitLoss()/(double)AMOUNT_MULTIPLIER);

                    List<ReportsLegDto> legDtoList = new ArrayList<>();
                    if (signal.getStrategyLeg() != null && !signal.getStrategyLeg().isEmpty()) {
                        for (StrategyLeg leg : signal.getStrategyLeg()) {
                            ReportsLegDto legDto = new ReportsLegDto();
                            legDto.setDate(leg.getCreatedAt());
                            legDto.setLegId(leg.getId());
                            legDto.setExchange("");
                            legDto.setSignalId(leg.getSignal().getId());
                            legDto.setInstrument(leg.getExchangeInstrumentId());
                            legDto.setQuantity(leg.getQuantity());
                            legDto.setPrice(Double.valueOf(leg.getPrice()));
                            legDtoList.add(legDto);
                        }
                        signalDto.setReportsLegDtoList(legDtoList);
                    }
                    map.put(formattedDate, signalDto);
                }
            }

            List<String> sortedDates = map.keySet().stream()
                    .sorted(Comparator.comparing(date -> LocalDate.parse(date, formatter)))
                    .toList();

            Double previousPnl = null;
            for (String date : sortedDates) {
                ReportsSignalDto currentDto = map.get(date);
                if (previousPnl != null) {
                    currentDto.setPnlChange(currentDto.getPnl() - previousPnl);
                } else {
                    currentDto.setPnlChange(0.0); // First date
                }
                previousPnl = currentDto.getPnl();
            }

            // Return the result in descending order (latest date first)
            List<ReportsSignalDto> sortedResponse = sortedDates.stream()
                    .sorted(Comparator.comparing(date -> LocalDate.parse(date.toString(), formatter)).reversed())
                    .map(map::get)
                    .toList();

            ReportsByStrategyResponseDto res = new ReportsByStrategyResponseDto();
            res.setReportsSignalDTOs(sortedResponse);
            return res;

        } catch (Exception e) {
            logger.error("Error fetching reports by strategyId : {}" + requestDto.getStrategyId(), e.getMessage(), e);
            throw new RuntimeException("Error fetching reports by strategyId :" + requestDto.getStrategyId(), e);
        }
    }

    public ReportsByStrategyResponseDto getReportsByStrategy(String clientId, ReportsByStrategyRequestDto requestDto) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Strategy strategy = strategyRepository.findById(requestDto.getStrategyId())
                .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + requestDto.getStrategyId()));

        if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
            throw new UnauthorizedAccessException("Cannot modify other user's strategy");
        }

        List<Signal> signalList = getSignalsByDateRangeAndStrategyIdAndUser(
                requestDto.getFromDate(),
                requestDto.getToDate(),
                requestDto.getStrategyId(),
                appUser.getId()
        );
        Double totalStrategyPNL = 0.0;
        Long totalOrders = 0L;

        ReportsByStrategyResponseDto reportsByStrategyResponseDto = new ReportsByStrategyResponseDto();
        List<ReportsSignalDto> reportsSignalDTOList = new ArrayList<>();

        for(Signal signal: signalList){
            if (signal.getProfitLoss() != null)
                totalStrategyPNL = totalStrategyPNL + (signal.getProfitLoss()/(double)AMOUNT_MULTIPLIER);
            ReportsSignalDto reportsSignalDto = createReportsSignalDto(signal);
            reportsSignalDto.setSequentialPNL(roundToTwoDecimalPlaces(totalStrategyPNL));
            reportsSignalDTOList.add(reportsSignalDto);
                totalOrders = totalOrders + reportsSignalDto.getOrderCount() ;
    }
        reportsByStrategyResponseDto.setStrategyName(strategy.getName());
        reportsByStrategyResponseDto.setStrategyID(strategy.getId());

        reportsByStrategyResponseDto.setTotalPNL(totalStrategyPNL);
        reportsByStrategyResponseDto.setTotalOrders(totalOrders);

        reportsByStrategyResponseDto.setReportsSignalDTOs(reportsSignalDTOList);
        return reportsByStrategyResponseDto;
    }

    public ReportsSignalDto createReportsSignalDto(Signal signal) {
        ReportsSignalDto reportsSignalDto = new ReportsSignalDto();
        double signalPNL = signal.getProfitLoss() != null? signal.getProfitLoss()/(double)AMOUNT_MULTIPLIER : 0.0;
        Long openLegCount = signal.getSignalLegs().stream().filter(strategyLeg ->
                strategyLeg.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())).count();
        String formattedDate = signal.getCreatedAt().atZone(zoneId).format(formatter);
        reportsSignalDto.setSignalId(signal.getId());
        reportsSignalDto.setPnl(signalPNL);
        reportsSignalDto.setOrderCount(openLegCount);
        reportsSignalDto.setDate(formattedDate);
        reportsSignalDto.setReportsLegDtoList(null);
        return reportsSignalDto;
    }
}
