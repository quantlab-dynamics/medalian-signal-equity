package com.quantlab.signal.technical;

import com.quantlab.signal.dto.redisDto.MarketData;
import lombok.Data;
import org.ta4j.core.*;
import org.ta4j.core.num.DoubleNum;
import java.time.*;
import java.util.Comparator;
import java.util.List;

public class FiveMinBarGenerator {

    public static OHLC buildCandle(List<MarketData> ticks) {

        if (ticks.isEmpty())
            return null;

        ticks.sort(Comparator.comparing(MarketData::getLut));

        MarketData first = ticks.get(0);
        MarketData last = ticks.get(ticks.size() - 1);

        ZonedDateTime start = Instant.ofEpochSecond(first.getLut()).atZone(ZoneId.of("Asia/Kolkata"));

        double open = first.getLTP();
        double close = last.getLTP();
        double high = ticks.stream().mapToDouble(MarketData::getLTP).max().orElse(open);
        double low = ticks.stream().mapToDouble(MarketData::getLTP).min().orElse(open);
        double volume = ticks.stream().mapToDouble(MarketData::getIV).sum();

   OHLC ohlc = new OHLC();
   ohlc.setOpen(open);
   ohlc.setHigh(high);
   ohlc.setLow(low);
   ohlc.setClose(close);
   ohlc.setVolume(volume);

return ohlc;
    }

}

@Data
class OHLC {
    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;
}
