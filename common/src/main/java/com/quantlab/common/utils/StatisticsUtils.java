package com.quantlab.common.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.quantlab.common.dto.*;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.dropdownutils.StatisticsMainTable;

public class StatisticsUtils {

    public static List<WeekStatsSummaryDto> getWeeklyPNLSummary(Strategy strategy) {
        LocalDate today = LocalDate.now();
        DayOfWeek currentDayOfWeek = today.getDayOfWeek();
        LocalDate weekStartDate = today.minusDays(currentDayOfWeek.getValue() - DayOfWeek.MONDAY.getValue());

        ZoneId zoneId = ZoneId.systemDefault(); // or choose your preferred zone

        List<Signal> weekRecords = strategy.getSignals()
                .stream()
                .filter(record -> {
                    Instant createdAt = record.getCreatedAt();
                    Instant start = weekStartDate.atStartOfDay(zoneId).toInstant();
                    Instant end = today.atTime(LocalTime.MAX).atZone(zoneId).toInstant();
                    return !createdAt.isBefore(start) && !createdAt.isAfter(end);
                })
                .collect(Collectors.toList());


        // Group records by DayOfWeek
        Map<DayOfWeek, List<Signal>> recordsByDay = weekRecords.stream()
                .collect(Collectors.groupingBy(record ->
                        record.getCreatedAt()
                                .atZone(zoneId)
                                .getDayOfWeek()
                ));


        List<WeekStatsSummaryDto> summary = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            LocalDate thisDay = weekStartDate.plusDays(day.getValue() - 1);

            if (thisDay.isAfter(today)) {
                summary.add(new WeekStatsSummaryDto(day.name(), 0, 0, 0));
                continue;
            }

            List<Signal> records = recordsByDay.getOrDefault(day, new ArrayList<>());
            if (records.isEmpty()) {
                summary.add(new WeekStatsSummaryDto(day.name(), 0, 0, 0));
            } else {
                double returns = records.stream().mapToDouble(Signal::getProfitLoss).sum();
                double maxProfit = records.stream().mapToDouble(Signal::getProfitLoss).max().orElse(0.0);
                double maxLoss = records.stream().mapToDouble(Signal::getProfitLoss).min().orElse(0.0);
                summary.add(new WeekStatsSummaryDto(day.name(), returns, maxProfit, maxLoss));
            }
        }

        return summary;
    }


    public static List<MontlyStatisticsDto> getFullMonthlyPNLSummary(Strategy strategy) {
        List<Signal> allRecords = strategy.getSignals();

        ZoneId zoneId = ZoneId.systemDefault();

        Map<YearMonth, List<Signal>> groupedByMonth = allRecords.stream()
                .collect(Collectors.groupingBy(record ->
                        YearMonth.from(record.getCreatedAt().atZone(zoneId))
                ));

        return groupedByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Optional: to sort by month
                .map(entry -> {
                    YearMonth yearMonth = entry.getKey();
                    List<Signal> records = entry.getValue();

                    int totalTrades = records.size();
                    double pnlRs = records.stream().mapToDouble(Signal::getProfitLoss).sum();
                    double pnlPercent = records.stream().mapToDouble(Signal::getProfitLoss).sum();

                    String monthLabel = yearMonth.getMonth() + " " + yearMonth.getYear(); // Example: JANUARY 2024

                    return new MontlyStatisticsDto(monthLabel, totalTrades, pnlRs, pnlPercent);
                })
                .collect(Collectors.toList());
    }


    public static StatisticsResponseDto<Number> calculateStatistics(Strategy strategy) {

        Long signalCount = strategy.getSignals().stream()
                .map(signal -> signal.getCreatedAt())
                .distinct()
                .count();
        Long noOfWinningTrades = strategy.getSignals().stream().filter(signal -> signal.getProfitLoss() > 0).count();
        Long noOfLossingDays = strategy.getSignals().stream().filter(signal -> signal.getProfitLoss() < 0).count();

        AtomicReference<Instant> lastWinDate = new AtomicReference<>(null);
        AtomicLong winStreak = new AtomicLong(0);
        AtomicLong maxWinningStreakDays = new AtomicLong(0);
        AtomicLong winDays = new AtomicLong(0);
        AtomicLong peakProfit = new AtomicLong(0);
        strategy.getSignals().stream()
                .collect(Collectors.groupingBy(
                        signal -> signal.getCreatedAt(),
                        Collectors.summingLong((Signal signal) -> signal.getProfitLoss())
                )).entrySet().stream()
                .filter(entry -> entry.getValue() > 0).forEach(entry -> {
                    winDays.incrementAndGet();
                    if(entry.getValue() > peakProfit.get()){
                        peakProfit.set(entry.getValue());
                    }
                    if(lastWinDate.get() == null) {
                        lastWinDate.set(entry.getKey());
                        winStreak.set(1);
                        maxWinningStreakDays.set(1);
                    } else {
                        long currentDays = entry.getKey().getEpochSecond() / (24 * 60 * 60);
                        long previousDays = lastWinDate.get().getEpochSecond() / (24 * 60 * 60);
                        if (currentDays - previousDays == 1) {
                            lastWinDate.set(entry.getKey());
                            winStreak.incrementAndGet();
                            maxWinningStreakDays.set(Math.max(maxWinningStreakDays.get(), winStreak.get()));
                        }else{
                            lastWinDate.set(entry.getKey());
                            winStreak.set(1);
                        }
                    }
                });

        AtomicReference<Instant> lastLossDate = new AtomicReference<>(null);
        AtomicLong lossStreak = new AtomicLong(0);
        AtomicLong maxLosingStreakDays = new AtomicLong(0);
        AtomicLong lossDays = new AtomicLong(0);
        AtomicLong peakLoss = new AtomicLong(0);
        strategy.getSignals().stream()
                .collect(Collectors.groupingBy(
                        signal -> signal.getCreatedAt(),
                        Collectors.summingLong((Signal signal) -> signal.getProfitLoss())
                )).entrySet().stream()
                .filter(entry -> entry.getValue() < 0).forEach(entry -> {
                    lossDays.incrementAndGet();
                    if(entry.getValue() < peakLoss.get()){
                        peakLoss.set(entry.getValue());
                    }
                    if(lastLossDate.get() == null) {
                        lastLossDate.set(entry.getKey());
                        lossStreak.set(1);
                        maxLosingStreakDays.set(1);
                    } else {
                        long currentDays = entry.getKey().getEpochSecond() / (24 * 60 * 60);
                        long previousDays = lastLossDate.get().getEpochSecond() / (24 * 60 * 60);
                        if (currentDays - previousDays == 1) {
                            lastLossDate.set(entry.getKey());
                            lossStreak.incrementAndGet();
                            maxLosingStreakDays.set(Math.max(maxLosingStreakDays.get(), lossStreak.get()));
                        }else{
                            lastLossDate.set(entry.getKey());
                            lossStreak.set(1);
                        }
                    }
                });

        Long DD = Math.abs(peakProfit.get() - peakLoss.get());
        Long DDPercent = peakProfit.get() !=0 ? (DD/peakProfit.get()) * 100 : 0L;
        List<StatisticsResponseObjDto<Number>> statisticsList = new ArrayList<>();
        statisticsList.add(new StatisticsResponseObjDto<Number>(StatisticsMainTable.MAX_DRAWDOWN_PERCENT.getLabel(), DDPercent));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Capital Required", strategy.getMinCapital()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Total Trading Days", signalCount));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Win Days", winDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Loss Days", lossDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Winning Streak Days", maxWinningStreakDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Losing Streak Days", maxLosingStreakDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Win Rate",signalCount != 0 ?  (((noOfWinningTrades/signalCount)*100.0)) : 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Loss Rate",signalCount != 0 ? (((noOfLossingDays/signalCount)*100.0)) : 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Monthly Profit", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Total Profit", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Monthly ROI", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Total ROI", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Standard Deviation (Annualised)", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Sharpe Ratio (Annualised)", null));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Sorting Ratio (Annualised)", null));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Profit In Day", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Loss In Day", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Profit/Loss Daily", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Profit On Profit Days", null));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Loss On Loss Days", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg no.of trades (Buy + Sell) per trading day", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Drawdown", DD));

        PerformanceOverviewDto  performanceOverviewDto = new PerformanceOverviewDto();
        if(winDays.get() != 0){
            performanceOverviewDto.setWinRatio((winDays.get()+lossDays.get())/winDays.get());
        }else{
            performanceOverviewDto.setWinRatio(0L);
        }
        performanceOverviewDto.setWinDays(winDays.get());
        performanceOverviewDto.setLossDays(lossDays.get());

        List<MontlyStatisticsDto> monthlyStatistics = getFullMonthlyPNLSummary(strategy);
        StatisticsResponseDto<Number> res = new StatisticsResponseDto<Number>();
        res.setMonthlyStatistics(monthlyStatistics);
        res.setStatistics(statisticsList);
        res.setWeekStatsSummary(getWeeklyPNLSummary(strategy));
        res.setPerformanceOverview(performanceOverviewDto);
        res.setProfitStatistics(new ProfitStatisticsDto());
        res.setRiskMetrics(new RiskMetricsDto(DD,0L,0L));
        return res;
    }
}
