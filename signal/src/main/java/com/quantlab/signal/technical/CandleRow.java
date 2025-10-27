package com.quantlab.signal.technical;

import lombok.Data;

@Data
public class CandleRow {
    private String name;
    private String instrument;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

}
