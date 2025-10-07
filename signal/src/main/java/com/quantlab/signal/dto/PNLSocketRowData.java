package com.quantlab.signal.dto;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.StrategyLeg;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PNLSocketRowData {
    long legId;
    Double legLTP;
    long legQuantity;
    Long lots;
    Double pAndL;
    Long signalId;
    Double executedPrice; //its called entry price in the ui;
    Double currentIV;
    Double currentDelta;
    String name;
    Double indexCurrentPrice;



    public PNLSocketRowData mapToPNLSocketRowData(LegHoldingDTO dto) {
        return new PNLSocketRowData(
                    dto.getLegId(), dto.getLegLTP(), dto.getLegQuantity(), dto.getLots(),
                    dto.getPAndL(), dto.getSignalId(),
                    dto.getExecutedPrice(), dto.getCurrentIV(), dto.getCurrentDelta(),
                    dto.getName(),dto.getIndexCurrentPrice()
            );
    }

}
