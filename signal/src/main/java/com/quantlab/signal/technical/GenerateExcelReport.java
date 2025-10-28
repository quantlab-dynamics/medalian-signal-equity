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
public class GenerateExcelReport implements CommandLineRunner{


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

    public void sendReport(List<CandleRow> rows, String toEmail , String coreMessage , int start , int end) throws Exception {
        byte[] excelBytes = this.generateExcel(rows);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(toEmail);
        helper.setSubject("Daily Report - "+ LocalDateTime.now().toLocalDate() +" - "+ LocalDateTime.now().toLocalTime());
        helper.setText(coreMessage);
        helper.setText("Please find attached the latest report.");

        helper.addAttachment("daily-report-"+LocalDateTime.now().toLocalDate()+"-"+LocalDateTime.now().toLocalTime()+"-List.xlsx", new ByteArrayResource(excelBytes));

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

    public static byte[] generateExcel(List<CandleRow> rows ) throws Exception {
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
        try (FileOutputStream fos = new FileOutputStream("candles_"+LocalDateTime.now().toLocalDate()+"list.xlsx")) {
            fos.write(byteArray);
        }

        workbook.close();
        return byteArray;
    }

    public List<CandleRow>  initiateReport(String sType , int start , int end)  {

        MarketData marketData = touchLineService.getTouchLine(String.valueOf(26000));
        Integer   atm = marketDataFetch.getATM("NIFTY", marketData.getLTP(), commonUtils.getExpiryShotDateByIndex(ExpiryType.CURRENT_WEEK.getKey(),"NIFTY",OptionType.OPTION.getKey()) );

        List<Integer> strikeList = redisIndexStrikePrices.get("NIFTY"+"_"+commonUtils.getExpiryShotDateByIndex(ExpiryType.CURRENT_WEEK.getKey(), "NIFTY", OptionType.OPTION.getKey()));

       Map<String, List<MarketData>> callTouchLine=  Arrays.stream(StrikeTypeMenu.values()).map(dto->{
            String strike = String.valueOf(diyStrategyCommonUtil.getStrike(dto.getKey(),"NIFTY",sType,atm,strikeList, ExpiryType.CURRENT_WEEK.getKey()));
            String newKey = "NIFTY" +
                    commonUtils.getExpiryShotDateByIndex(ExpiryType.CURRENT_WEEK.getKey(), "NIFTY", OptionType.OPTION.getKey()) +
                    "-" + strike +  sType;

//            logger.info("keys generated: "+newKey);
            System.out.println("keys generated: "+newKey);
            MasterResponseFO master = marketDataFetch.getMasterResponse(newKey);
            List<MarketData> ticks = tickRepository.findAll(String.valueOf(master.getExchangeInstrumentID()));
            ticks= filterTicksByTimeRange(ticks , start , end);
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

        return rows;
    }

    public void getAlldata(int start, int end){
List<CandleRow> rows = this.initiateReport("CE"  , start,end);
rows.addAll(this.initiateReport("PE" , start,end));
        try {
//            byte[] excelBytes = this.generateExcel(rows);
            this.sendReport(rows,"bhargava2211@gmail.com","9:15 to 9:30 report"  , start,end );
            this.sendReport(rows,"kpdasari@gmail.com","9:15 to 9:30 report" , start,end);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<MarketData> filterTicksByTimeRange(List<MarketData> ticks , int startTime , int endTime) {

        LocalTime start = LocalTime.of(9, startTime);
        LocalTime end = LocalTime.of(9, endTime);

        return ticks.stream()
                .filter(t -> {

                    LocalTime tickTime = LocalDateTime.ofEpochSecond(
                            t.getLut(),
                            0,
                            ZoneOffset.UTC
                    ).toLocalTime();

                    return !tickTime.isBefore(start) && tickTime.isBefore(end);
                })
                .collect(Collectors.toList());
    }

    @Scheduled(cron = "0 20 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void dailyReport() {
        getAlldata(15,20);
    }

    @Scheduled(cron = "0 25 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void dailyReport930() {
        getAlldata(20,25);
    }

    @Override
    public void run(String... args) throws Exception {
       // getAlldata(15,20);
    }
}

