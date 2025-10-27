package com.quantlab.signal.technical;

import com.quantlab.common.utils.staticstore.dropdownutils.ExpiryType;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.common.utils.staticstore.dropdownutils.SegmentType;
import com.quantlab.common.utils.staticstore.dropdownutils.StrikeTypeMenu;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.TickRepository;
import com.quantlab.signal.service.redisService.TouchLineRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.utils.DiyStrategyCommonUtil;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.mail.internet.MimeMessage;
import lombok.Data;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.quantlab.signal.utils.staticdata.StaticStore.redisIndexStrikePrices;

@Component
public class GenerateExcelReport implements CommandLineRunner {


    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private TickRepository tickRepository;

    @Autowired
    private DiyStrategyCommonUtil diyStrategyCommonUtil;

    @Autowired
    private TouchLineRepository touchLineRepository;

    @Autowired
    private TouchLineService touchLineService;

    public void sendReport(List<CandleRow> rows, String toEmail , String coreMessage) throws Exception {
        byte[] excelBytes = this.generateExcel(rows);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(toEmail);
        helper.setSubject("Daily Report");
        helper.setText(coreMessage);
        helper.setText("Please find attached the latest report.");

        helper.addAttachment("report.xlsx", new ByteArrayResource(excelBytes));

        mailSender.send(message);
    }
    public List<CandleRow>  getCandleRow(Map<String,List<MarketData>> marketData) {

        List<CandleRow> candleRows = new ArrayList<>();

for (
     Map.Entry<String, List<MarketData>> instrument : marketData.entrySet() ) {

        OHLC bar = FiveMinBarGenerator.buildCandle(instrument.getValue());

            CandleRow row = new CandleRow();
            row.setName(instrument.getKey());
            row.setInstrument(instrument.getKey());
            row.setOpen(bar.getOpen());
            row.setHigh(bar.getHigh());
            row.setLow(bar.getLow());
            row.setClose(bar.getClose());
            row.setVolume(bar.getVolume());
            candleRows.add(row);
        }

return candleRows;
    }

    public static byte[] generateExcel(List<CandleRow> rows) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Report");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Instrument");
        headerRow.createCell(1).setCellValue("open");
        headerRow.createCell(2).setCellValue("high");
        headerRow.createCell(3).setCellValue("low");
        headerRow.createCell(4).setCellValue("close");

        int rowIndex = 1;
        for (CandleRow row : rows) {
            Row excelRow = sheet.createRow(rowIndex++);
            excelRow.createCell(0).setCellValue(row.getInstrument());
            excelRow.createCell(1).setCellValue(row.getOpen());
            excelRow.createCell(2).setCellValue(row.getHigh());
            excelRow.createCell(3).setCellValue(row.getLow());
            excelRow.createCell(4).setCellValue(row.getClose());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        byte[] byteArray = out.toByteArray();

        // Save locally
        try (FileOutputStream fos = new FileOutputStream("ticks_candles.xlsx")) {
            fos.write(byteArray);
        }

        workbook.close();
        return byteArray;
    }

    public void initiateReport()  {

        MarketData marketData = touchLineService.getTouchLine(String.valueOf(26000));
        Integer   atm = marketDataFetch.getATM("NIFTY", marketData.getLTP(), commonUtils.getExpiryShotDateByIndex(ExpiryType.CURRENT_WEEK.getKey(),"NIFTY",OptionType.OPTION.getKey()) );

        List<Integer> strikeList = redisIndexStrikePrices.get("NIFTY"+"_"+commonUtils.getExpiryShotDateByIndex(ExpiryType.CURRENT_WEEK.getKey(), "NIFTY", OptionType.OPTION.getKey()));

       Map<String, List<MarketData>> callTouchLine=  Arrays.stream(StrikeTypeMenu.values()).map(dto->{
            String strike = String.valueOf(diyStrategyCommonUtil.getStrike(dto.getKey(),"NIFTY","CE",atm,strikeList, ExpiryType.CURRENT_WEEK.getKey()));
            String newKey = "NIFTY" +
                    commonUtils.getExpiryShotDateByIndex(ExpiryType.CURRENT_WEEK.getKey(), "NIFTY", OptionType.OPTION.getKey()) +
                    "-" + strike +  "CE";

//            logger.info("keys generated: "+newKey);
            System.out.println("keys generated: "+newKey);
            MasterResponseFO master = marketDataFetch.getMasterResponse(newKey);
            List<MarketData> ticks = tickRepository.findAll(String.valueOf(master.getExchangeInstrumentID()));
            ticks= filterTicksByTimeRange(ticks);
                   Map<String, List<MarketData>> singleMap = new HashMap<>();
                   singleMap.put(master.getName(), ticks);
                   return singleMap;
               })
               .flatMap(m -> m.entrySet().stream())
               .collect(Collectors.toMap(
                       Map.Entry::getKey,
                       Map.Entry::getValue,
                       (list1, list2) -> {        // Merge duplicate keys
                           List<MarketData> merged = new ArrayList<>(list1);
                           merged.addAll(list2);
                           return merged;
                       }
               ));
        System.out.println(callTouchLine);
        List<CandleRow> rows = this.getCandleRow(callTouchLine);
try {

    this.sendReport(rows,"bhargava2211@gmail.com","9:15 to 9:30 report");
    this.sendReport(rows,"kp@gmail.com","9:15 to 9:30 report");
} catch (Exception e) {
    throw new RuntimeException(e);
}
    }

    @Override
    public void run(String... args) throws Exception {
        this.initiateReport();
    }


    public static List<MarketData> filterTicksByTimeRange(List<MarketData> ticks) {

        LocalTime start = LocalTime.of(9, 15);
        LocalTime end = LocalTime.of(12, 30);

        return ticks.stream()
                .filter(t -> {

                    LocalTime tickTime = LocalDateTime.ofEpochSecond(
                            t.getLut(),
                            0,
                            ZoneOffset.UTC // do NOT apply IST again
                    ).toLocalTime();

                    return !tickTime.isBefore(start) && tickTime.isBefore(end);
                })
                .collect(Collectors.toList());
    }

    @Scheduled(cron = "0 30 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void dailyReport() {
        initiateReport();
    }
}

