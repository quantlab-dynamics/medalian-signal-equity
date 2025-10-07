package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum StrategyStatus {
    EXITED_MANUALLY("Exited-Manually","Exited-Manually"),
    EXIT("exit","Exit"),
    REJECTED("Rejected","Rejected"),
    CANCELLED("cancelled","Cancelled"),
    PENDING("pending","Pending"),
    RETRY("retry","Retry");

    private final String key;
    private final String label;

    StrategyStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
