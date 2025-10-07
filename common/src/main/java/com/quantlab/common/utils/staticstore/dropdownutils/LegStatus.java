package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum LegStatus {
    OPEN("live", "Live"),
    EXIT("exit", "Exit"),
    EXCHANGE("leg-placed", "Leg Placed"),
    TYPE_OPEN("open", "Open"),
    TYPE_EXIT("exit", "Exit");

    private final String key;
    private final String label;

    LegStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}