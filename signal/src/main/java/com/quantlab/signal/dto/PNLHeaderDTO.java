package com.quantlab.signal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PNLHeaderDTO {
    Double todaysPAndL;
    Double OverAllUserPAndL;
    Double positionalPAndL;
    Double intradayPAndL;
    Double deployedCapital;
}
