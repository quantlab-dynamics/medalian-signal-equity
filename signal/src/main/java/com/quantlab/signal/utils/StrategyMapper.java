package com.quantlab.signal.utils;

import lombok.Getter;

@Getter
public enum StrategyMapper {


    DELTA_NEUTRAL("DN","DeltaNeutralStrategy"),
    DELTA_NEUTRAL_HEDGE("DNH","DeltaNeutralStrategyHedge"),
    DIY("DIY","DiyStrategy"),
    ROLLING_STRADDLE("RS","RollingStraddle");

    private final String key;
    private final String label;

    StrategyMapper(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
