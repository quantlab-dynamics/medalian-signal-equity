package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatisticsResponseObjDto<T> {
    private String name;
    private T value;
}
