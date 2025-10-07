package com.quantlab.client.utils;

import com.quantlab.client.dto.EntryDetailsDto;
import com.quantlab.client.dto.ExitDetailsDto;
import com.quantlab.client.dto.StrategyDto;
import com.quantlab.client.dto.StrategyLegDto;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Component
public class UserStrategyUtils {
    private static final Logger log = LoggerFactory.getLogger(UserStrategyUtils.class);

    private final ModelMapper modelMapper;

    @Autowired  // Optional if only one constructor, Spring will auto-wire
    public UserStrategyUtils(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public StrategyLegDto convertToStrategyLegDto(StrategyLeg strategyLeg) {
        StrategyLegDto strategyLegDto = new StrategyLegDto();
        strategyLegDto.setId(strategyLeg.getId());
        strategyLegDto.setPositions(strategyLeg.getBuySellFlag());
        strategyLegDto.setOptionType(strategyLeg.getOptionType());
        strategyLegDto.setLots(strategyLeg.getNoOfLots());
        strategyLegDto.setExpiry(strategyLeg.getLegExpName());
        strategyLegDto.setStrikeType(strategyLeg.getSktType());
        strategyLegDto.setStrikeSelection(strategyLeg.getSktSelection());
        if (strategyLeg.getSktSelectionValue() != null) strategyLegDto.setStrikeSelectionValue(strategyLeg.getSktSelectionValue());
        strategyLegDto.setTgtToggle(strategyLeg.getTargetUnitToggle());
        strategyLegDto.setTgtType(strategyLeg.getTargetUnitType());
        strategyLegDto.setTgtValue(strategyLeg.getTargetUnitValue() != null ? strategyLeg.getTargetUnitValue().toString() : null);
        strategyLegDto.setStopLossToggle(strategyLeg.getStopLossUnitToggle());
        strategyLegDto.setStopLossType(strategyLeg.getStopLossUnitType());
        strategyLegDto.setStopLossValue(strategyLeg.getStopLossUnitValue() != null ? strategyLeg.getStopLossUnitValue().toString() : null);
        strategyLegDto.setTslToggle(strategyLeg.getTrailingStopLossToggle());
        strategyLegDto.setTslType(strategyLeg.getTrailingStopLossType());
        strategyLegDto.setTslValue(strategyLeg.getTrailingStopLossValue());
        strategyLegDto.setTdValue(strategyLeg.getTrailingDistance());
        strategyLegDto.setDerivativeType(strategyLeg.getDerivativeType());
        return strategyLegDto;
    }

    // Map EntryDetails and ExitDetails to DTOs
    public void mapEntryAndExitDetails(Strategy strategy, StrategyDto strategyDto) {
        if (strategy.getEntryDetails() != null) {
            strategyDto.setEntryDetails(modelMapper.map(strategy.getEntryDetails(), EntryDetailsDto.class));
            if(strategy.getEntryDetails().getEntryDays() != null && !strategy.getEntryDetails().getEntryDays().isEmpty()){
                List<Long> entryDaysList = new ArrayList<>();
                strategy.getEntryDetails().getEntryDays().forEach(day -> {
                    entryDaysList.add(day.getId());
                });
                strategyDto.getEntryDetails().setEntryDaysList(entryDaysList);
            }
        } else {
            log.warn("Entry Details not found for strategy ID: {}", strategy.getId());
        }

        if (strategy.getExitDetails() != null) {
            strategyDto.setExitDetails(modelMapper.map(strategy.getExitDetails(), ExitDetailsDto.class));
        } else {
            log.warn("Exit Details not found for strategy ID: {}", strategy.getId());
        }
    }

    // Map StrategyLeg
    public void mapStrategyLeg(List<StrategyLeg> defaultLegs, StrategyDto strategyDto) {
        if (defaultLegs != null && !defaultLegs.isEmpty()) {
            List<StrategyLegDto> strategyLegDtos = defaultLegs.stream()
                    .map(this::convertToStrategyLegDto)
                    .collect(Collectors.toList());
            strategyDto.setStrategyLegs(strategyLegDtos);
        }
    }

    // Categorize Strategy
    public void categorizeStrategy(Strategy strategy, StrategyDto strategyDto,
                                   List<StrategyDto> diyList, List<StrategyDto> inHouseList, List<StrategyDto> prebuiltList, List<StrategyDto> popularList) {
        if (strategy.getStrategyCategory().getId() == 1L) {
            diyList.add(strategyDto);
        } else if (strategy.getStrategyCategory().getId() == 2L) {
            inHouseList.add(strategyDto);
        } else if (strategy.getStrategyCategory().getId() == 3L) {
            prebuiltList.add(strategyDto);
        } else if (strategy.getStrategyCategory().getId() == 4L) {
            popularList.add(strategyDto);
        } else {
            log.warn("Unknown strategy category for strategy ID: {}", strategy.getId());
        }
    }

    public StrategyDto converToStrategyDto(Strategy strategy, List<StrategyLeg> defaultLegs) {
        if (strategy == null) {
            return null;
        }

        StrategyDto strategyDto = new StrategyDto();

        strategyDto.setId(strategy.getId());
        strategyDto.setName(strategy.getName());
        strategyDto.setDescription(strategy.getDescription());
        strategyDto.setAtmType(strategy.getAtmType());
        strategyDto.setMultiplier(strategy.getMultiplier());
        strategyDto.setMinCapital(strategy.getMinCapital()/AMOUNT_MULTIPLIER);
        if (strategy.getUnderlying() != null) {
            strategyDto.setUnderlying(strategy.getUnderlying().getId().toString());
        }
        strategyDto.setTypeOfStrategy(strategy.getTypeOfStrategy());
        strategyDto.setPositionType(strategy.getPositionType());
        strategyDto.setExecutionType(strategy.getExecutionType());
//        strategyDto.setReSignalCount(strategy.getReSignalCount());
        strategyDto.setCreatedAt(strategy.getCreatedAt());
        strategyDto.setStrategyTag(strategy.getStrategyTag());
        strategyDto.setStatus(strategy.getStatus());
        strategyDto.setSubscription(strategy.getSubscription());
        String category;
        if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()))
            category =  "In-House";
        else if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.DIY.getKey()))
                category = "DIY";
            else
                category = strategy.getCategory();

        strategyDto.setCategory(category);
        strategyDto.setDrawDown(strategy.getDrawDown());

        // Map additions
//        if(strategy.getStrategyAdditions() != null){
//            strategyDto.setDeltaSlippage(strategy.getStrategyAdditions().getDeltaSlippage());
//        }

        // Map EntryDetails
        if (strategy.getEntryDetails() != null) {
            EntryDetailsDto entryDetailsDto = new EntryDetailsDto();
            entryDetailsDto.setEntryTime(strategy.getEntryDetails().getEntryTime());
            entryDetailsDto.setExpiry(strategy.getExpiry());
            // has to change
            entryDetailsDto.setEntryMinsTime(strategy.getEntryDetails().getEntryMinsTime());
            entryDetailsDto.setEntryHourTime(strategy.getEntryDetails().getEntryHourTime());
            if(strategy.getEntryDetails().getEntryDays() != null && !strategy.getEntryDetails().getEntryDays().isEmpty()){
                List<Long> entryDaysList = new ArrayList<>();
                strategy.getEntryDetails().getEntryDays().forEach(day -> {
                    entryDaysList.add(day.getId());
                });
                entryDetailsDto.setEntryDaysList(entryDaysList);
            }
            strategyDto.setEntryDetails(entryDetailsDto);
        } else {
            log.warn("Entry Details not found for strategy ID: {}", strategy.getId());
        }

        // map exit details
        if (strategy.getExitDetails() != null) {
            strategyDto.setExitDetails(modelMapper.map(strategy.getExitDetails(), ExitDetailsDto.class));
        } else {
            log.warn("Exit Details not found for strategy ID: {}", strategy.getId());
        }

        mapStrategyLeg(defaultLegs, strategyDto);

        return strategyDto;
    }

}
