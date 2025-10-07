package com.quantlab.signal.dto;

import com.quantlab.signal.dto.redisDto.PNLLegTableDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PNLHoldingDTO {
    String userID;
    Double todaysPAndL;
    Double OverAllUserPAndL;
    Double postionalPAndL;
    Double intradayPAndL;
    Double deployedCapital;
    PNLHeaderDTO liveHeaders;
    PNLHeaderDTO forwardHeaders;
    ArrayList<PNLLegTableDTO> strategyLegs = new ArrayList<>();

    public PNLHoldingDTO mapToPNLHoldingDTO(HoldingsDTO dto) {
        return dto == null ? null : new PNLHoldingDTO(
                dto.getUserID(), dto.getTodaysPAndL(),
                dto.getOverAllUserPAndL(), dto.getPostionalPAndL(),
                dto.getIntradayPAndL(), dto.getDeployedCapital(), dto.liveHeaders,
                dto.getForwardHeader(), new ArrayList<>()
        );
    }

    public void setStrategyLegs(ArrayList<StrategyLegTableDTO> strategyLegTableDTOs){
        for (StrategyLegTableDTO legDto : strategyLegTableDTOs){
            this.strategyLegs.add(new PNLLegTableDTO(legDto));
        }
    }
}
