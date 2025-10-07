package com.quantlab.client.service;

import com.quantlab.client.dto.*;
import com.quantlab.client.utils.UserStrategyUtils;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.*;

import com.quantlab.common.loggingService.DeploymentErrorService;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import com.quantlab.signal.dto.DeployedErrorDTO;
import com.quantlab.signal.dto.StrategyErrorDetails;
import com.quantlab.signal.service.AuthService;
import com.quantlab.signal.strategy.driver.Parser;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.EXCEPTION_DATE;


@Service("UserNameService")
@Transactional
public class UserStrategyService {

    private static final Logger log = LoggerFactory.getLogger(UserStrategyService.class);

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    EntryDetailsRepository entryDetailsRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    private Parser parser;

    @Autowired
    UnderlyingRespository underlyingRespository;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    UserStrategyUtils userStrategyUtils;

    @Autowired
    AuthService authService;

    @Autowired
    UserSignalService userSignalService;

    @Autowired
    DeploymentErrorService deploymentErrorService;

    @Transactional
    public DeployDropdownDto getDeployDropdownList(boolean isLimited){
        log.info("Entering getDeployDropdownList() with isLimited: {}", isLimited);

        List<Underlying> underlyingList = underlyingRespository.findAll();
        List<EntryDays> entryDaysList = entryDaysRespository.findAll(Sort.by(Sort.Order.asc("id")));

        DeployDropdownDto deployDropdownDto = new DeployDropdownDto();
        // Conditionally filter ATM Types for deploySave
        List<AtmType> filteredAtmTypes = isLimited
                ? List.of(AtmType.SPOT_ATM, AtmType.FUTURE_ATM, AtmType.SYNTHETIC_ATM)
                : Arrays.asList(AtmType.values());

        deployDropdownDto.setAtmType(filteredAtmTypes.stream()
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

//        deployDropdownDto.setMultiplier(Arrays.stream(MultiplierMenu.values())
//                .map(type -> new SelectionMenuLongDto(type.getKey(), type.getLabel()))
//                .toList());
        List<SelectionMenuLongDto> multipliers = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> new SelectionMenuLongDto((long) i, i + "x"))
                .toList();
        deployDropdownDto.setMultiplier(multipliers);

        if (!underlyingList.isEmpty()) {
            deployDropdownDto.setUnderlying(underlyingList.stream()
                    .map(list -> new SelectionMenuLongDto(list.getId(), list.getName()))
                    .toList());
        }

        deployDropdownDto.setOrder(Arrays.stream(StrategyType.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        deployDropdownDto.setExecutionType(Arrays.stream(ExecutionTypeMenu.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        deployDropdownDto.setExpiry(Arrays.stream(ExpiryType.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        if (!entryDaysList.isEmpty()) {
            deployDropdownDto.setEntryDays(entryDaysList.stream()
                    .map(list -> new SelectionMenuLongDto(list.getId(), list.getDay()))
                    .toList());
        }

        deployDropdownDto.setMtmType(Arrays.stream(MtmMenu.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        log.info("Exiting getDeployDropdownList()");
        return deployDropdownDto;
    }


    @Transactional
    public AllStrategiesResDto getAllStrategies(String clientId, boolean isLimited) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        AllStrategiesResDto res = new AllStrategiesResDto();

        // Lists to categorize strategies
        List<StrategyDto> diyList = new ArrayList<>();
        List<StrategyDto> inHouseList = new ArrayList<>();
        List<StrategyDto> prebuiltList = new ArrayList<>();
        List<StrategyDto> popularList = new ArrayList<>();

        try {
            // Fetch strategies based on user ID
            List<Strategy> strategies2 = strategyRepository.findAllByAppUser_IdOrderByIdAsc(appUser.getAppUserId());
            List<Strategy> strategies = strategies2.stream().filter(strategy -> !strategy.getIsHidden()).toList();
            log.info("Found {} strategies for user ID {}", strategies.size(), appUser.getAppUserId());
            for (Strategy strategy : strategies) {
                // Convert Strategy to StrategyDto
                List<StrategyLeg> defaultLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
                StrategyDto strategyDto = userStrategyUtils.converToStrategyDto(strategy, defaultLegs);

                // Categorize strategies based on category ID
                userStrategyUtils.categorizeStrategy(strategy, strategyDto, diyList, inHouseList, prebuiltList, popularList);
            }

            // Assemble the response DTO with categorized strategies
            AllStrategyCategoryDto allCategoryDto = new AllStrategyCategoryDto();
            allCategoryDto.setDiy(diyList);
            allCategoryDto.setInHouse(inHouseList);
            allCategoryDto.setPreBuilt(prebuiltList);
            allCategoryDto.setPopular(popularList);
            res.setStrategies(allCategoryDto);

            // Get additional dropdown data
            DeployDropdownDto deployDropdownDto = getDeployDropdownList(isLimited);
            res.setDropdownList(deployDropdownDto);

            return res; // Return the populated response

        } catch (Exception e) {
            log.error("Error fetching strategies for user ID {}: {}", appUser.getAppUserId(), e.getMessage());
            throw new RuntimeException("Failed to fetch strategies", e); // Handle this gracefully or rethrow as a custom exception
        }
    }


    @Transactional
    public AllStrategiesResDto deploySaveStrategy(String clientId, DeployReqDto deployReqDto) {
        log.info("Deploying strategy with ID: " + deployReqDto.getStrategyId());

        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(deployReqDto.getStrategyId())
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + deployReqDto.getStrategyId()));

            if (!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())) {
                throw new UnauthorizedAccessException("Cannot modify other user's strategy");
            }

            LocalDate date = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

            // Basic strategy updates
            strategy.setAtmType(deployReqDto.getAtmType());
            strategy.setMinCapital(deployReqDto.getMinCapital() * AMOUNT_MULTIPLIER);
            strategy.setPositionType(deployReqDto.getOrderId());
            strategy.setMultiplier(deployReqDto.getMultiplier());
            strategy.setExecutionType(deployReqDto.getExecutionTypeId());
//            strategy.setReSignalCount(deployReqDto.getFreshEntryCount());
            strategy.setExpiry(deployReqDto.getExpiry());
            strategy.setSubscription(SubscriptionStatus.START.getKey());
            strategy.setLastDeployedOn(date.format(formatter));
            strategy.setManualExitType(ManualExit.DISABLED.getKey());
            if (strategy.getStatus().equalsIgnoreCase(Status.INACTIVE.getKey())) {
                strategy.setStatus(Status.STANDBY.getKey());
            }

            // Underlying update
            Underlying underlying = underlyingRespository.findById(deployReqDto.getIndex())
                    .orElseThrow(() -> new UnderlyingNotFoundException("Underlying asset not found for ID: " + deployReqDto.getIndex()));
            strategy.setUnderlying(underlying);

            EntryDetails entryDetails = strategy.getEntryDetails();
            if (entryDetails == null) {
                entryDetails = new EntryDetails();
                entryDetails.setStrategy(strategy);  // Maintain bidirectional relationship
                strategy.setEntryDetails(entryDetails);
            }

            entryDetails.setEntryHourTime(deployReqDto.getEntryHours());
            entryDetails.setEntryMinsTime(deployReqDto.getEntryMinutes());
            LocalDateTime localDateTime = LocalDateTime.now()
                    .withHour(deployReqDto.getEntryHours())   // Set the hour
                    .withMinute(deployReqDto.getEntryMinutes()) // Set the minutes
                    .withSecond(0) // Optional: Reset seconds to 0
                    .withNano(0);  // Optional: Reset nanoseconds to 0

            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
            entryDetails.setEntryTime(zonedDateTime.toInstant());
            // Properly handle the EntryDays collection (over writing the existing data with the new data.)
            List<EntryDays> entryDaysList = entryDaysRespository.findByIdIn(deployReqDto.getDays());
            entryDetails.setEntryDays(new ArrayList<>());
//            entryDetails.getEntryDays().clear();
            entryDetails.getEntryDays().addAll(entryDaysList);

            DayOfWeek today = LocalDate.now().getDayOfWeek();
            boolean isTodayEntryDay = entryDaysList.stream()
                    .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(today.name()));

            if (!isTodayEntryDay && EXCEPTION_DATE != null)
                isTodayEntryDay = EXCEPTION_DATE.equals(LocalDate.now());

            if (isTodayEntryDay && !Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                strategy.setStatus(Status.ACTIVE.getKey());
            }else if (!Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                strategy.setStatus(Status.STANDBY.getKey());
            }

            // MTM calculations
            Long profitMtmUnitValue = deployReqDto.getProfitMtmValue();
            Long stoplossMtmValue = deployReqDto.getStoplossValue();
            if(deployReqDto.getProfitMtmType().equalsIgnoreCase(PERCENTOFCAPITAL)){
                double res;
                if(strategy.getMinCapital() != null)
                    res = (double) strategy.getMinCapital() / deployReqDto.getProfitMtmValue();
                else // make MinCapital 1 lakh if getMinCapital is null
                    res = (double) 100000 / deployReqDto.getProfitMtmValue();
                profitMtmUnitValue = Math.round(res);
            }
            if(deployReqDto.getStoplossType().equalsIgnoreCase(PERCENTOFCAPITAL)){
                double res;
                if(strategy.getMinCapital() != null)
                    res = (double) strategy.getMinCapital() / deployReqDto.getStoplossValue();
                else // make MinCapital 1 lakh if getMinCapital is null
                    res = (double) 100000 / deployReqDto.getStoplossValue();
                stoplossMtmValue = Math.round(res);
            }

            // Exit details update
            ExitDetails exitDetails = strategy.getExitDetails();
            if (exitDetails == null) {
                exitDetails = new ExitDetails();
                exitDetails.setStrategy(strategy);
                strategy.setExitDetails(exitDetails);
            }

            LocalDateTime localDateTimeExit = LocalDateTime.now()
                    .withHour(deployReqDto.getExitHours())   // Set the hour
                    .withMinute(deployReqDto.getExitMinutes()) // Set the minutes
                    .withSecond(0) // Optional: Reset seconds to 0
                    .withNano(0);  // Optional: Reset nanoseconds to 0

            ZonedDateTime zonedDateTimeExit = localDateTimeExit.atZone(ZoneId.systemDefault());
            exitDetails.setExitHourTime(deployReqDto.getExitHours());
            exitDetails.setExitMinsTime(deployReqDto.getExitMinutes());
            exitDetails.setExitTime(zonedDateTimeExit.toInstant());
            exitDetails.setTargetUnitToggle(deployReqDto.getProfitMtmToggle());
            exitDetails.setTargetUnitType(deployReqDto.getProfitMtmType());
            exitDetails.setTargetUnitValue(deployReqDto.getProfitMtmValue());
            exitDetails.setStopLossUnitToggle(deployReqDto.getStoplossToggle());
            exitDetails.setStopLossUnitType(deployReqDto.getStoplossType());
            exitDetails.setStopLossUnitValue(deployReqDto.getStoplossValue());
            exitDetails.setProfitMtmUnitValue(profitMtmUnitValue);
            exitDetails.setStoplossMtmUnitValue(stoplossMtmValue);

            List<StrategyLeg> legs = strategyLegRepository.findByStrategyIdAndLegType(strategy.getId(), StrategyCategoryType.DIY.getKey());

            List<StrategyLeg> newLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
            for (StrategyLeg leg : newLegs) {
                log.info("Processing leg: {}", leg.getId());
                if (leg.getLegType().equalsIgnoreCase(StrategyCategoryType.DIY.getKey())) {
                    log.info("Processing leg in side the if : {}", leg.getId());
                   leg.setLegExpName(deployReqDto.getExpiry());
                }
            }

            // Strategy additions
//            if (strategy.getStrategyAdditions() == null) {
//                StrategyAdditions strategyAdditions = new StrategyAdditions();
//                strategyAdditions.setStrategy(strategy);
//                strategyAdditions.setDeltaSlippage(deployReqDto.getDeltaSlippage());
//                strategy.setStrategyAdditions(strategyAdditions);
//            }
//            else {
//                strategy.getStrategyAdditions().setDeltaSlippage(deployReqDto.getDeltaSlippage());
//            }
            // Save the strategy
            strategyLegRepository.saveAll(newLegs);
            strategy = strategyRepository.saveAndFlush(strategy);
            deploymentErrorService.saveStrategyUpdateLogs(strategy, "Entry time met; strategy is now live");

            return getAllStrategies(clientId, true);
        } catch (StrategyNotFoundException | UnauthorizedAccessException | UnderlyingNotFoundException e) {
            log.warn("Error during deployment: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error during strategy save operation", e);
            throw new RuntimeException("Failed to deploy strategy: " + e.getMessage(), e);
        }
    }

    @Transactional
    public AllStrategiesResDto oneClickDeploy(String clientId, OneClickDeployDto oneClickDeployDto) {
        log.info("Initiating one-click deploy for strategy with ID: {}", oneClickDeployDto.getStrategyId());

        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            // Fetch the strategy
            Strategy strategy = strategyRepository.findById(oneClickDeployDto.getStrategyId())
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + oneClickDeployDto.getStrategyId()));
            if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
                throw new Exception("Cannot deploy other user's strategy");
            }


            // Check if the strategy is already live
            if (Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                throw new StrategyAlreadyLiveException("Strategy is already live for ID: " + oneClickDeployDto.getStrategyId());
            }
            Hibernate.initialize(strategy.getEntryDetails());

            EntryDetails entryDetails = strategy.getEntryDetails();


            LocalDateTime localDateTime = LocalDateTime.now()
                    .withHour(entryDetails.getEntryHourTime())   // Set the hour
                    .withMinute(entryDetails.getEntryMinsTime()) // Set the minutes
                    .withSecond(0) // Optional: Reset seconds to 0
                    .withNano(0);  // Optional: Reset nanoseconds to 0

            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
            entryDetails.setEntryTime(zonedDateTime.toInstant());


            List<EntryDays> entryDaysList = entryDetails.getEntryDays();
//            if (entryDetails.getEntryDays() == null) {
//                entryDetails.setEntryDays(new ArrayList<>());
//            }
//            entryDetails.getEntryDays().clear();
//            entryDetails.getEntryDays().addAll(entryDaysList);

            List<StrategyLeg> newLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
            for (StrategyLeg leg : newLegs) {
                log.info("Processing leg: {}", leg.getId());
                if (leg.getLegType().equalsIgnoreCase(StrategyCategoryType.DIY.getKey())) {
                    log.info("Processing leg in side the if : {}", leg.getId());
                    leg.setLegExpName(strategy.getExpiry());
                }
            }

            strategyLegRepository.saveAll(newLegs);
            DayOfWeek today = LocalDate.now().getDayOfWeek();
            boolean isTodayEntryDay = entryDaysList.stream()
                    .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(today.name()));
            if (!isTodayEntryDay && EXCEPTION_DATE != null)
                isTodayEntryDay = EXCEPTION_DATE.equals(LocalDate.now());

            if (isTodayEntryDay && !Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                strategy.setStatus(Status.ACTIVE.getKey());
            }else {
                strategy.setStatus(Status.STANDBY.getKey());
            }
            if (!SubscriptionStatus.START.getKey().equalsIgnoreCase(strategy.getSubscription())) {
                LocalDate date = LocalDate.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

                strategy.setLastDeployedOn(date.format(formatter));
                strategy.setSubscription(SubscriptionStatus.START.getKey());
                strategy.setMultiplier(oneClickDeployDto.getMultiplier());
                strategy.setExecutionType(oneClickDeployDto.getExecutionTypeId());
                strategy.setManualExitType(ManualExit.DISABLED.getKey());
                entryDetailsRepository.save(entryDetails);
                strategy = strategyRepository.save(strategy);
                deploymentErrorService.saveStrategyUpdateLogs(strategy, "oneClick strategy deployed; strategy is now active");
            }

 //           parser.runStrategy(strategy);

            log.info("Strategy ID: {} deployed successfully.", oneClickDeployDto.getStrategyId());

            // Return the updated strategies list
            return getAllStrategies(clientId, true); // Make sure this method handles empty cases or returns an Optional
        } catch (StrategyNotFoundException | StrategyAlreadyLiveException e) {
            log.warn("Deployment failed for strategy with ID: {} - {}", oneClickDeployDto.getStrategyId(), e.getMessage());
            throw e;  // Re-throw the exception for global handling
        } catch (Exception e) {
            log.error("Unexpected error during one-click deployment for strategy ID: {}", oneClickDeployDto.getStrategyId(), e);
            throw new RuntimeException("Failed to deploy strategy due to unexpected error", e);
        }
    }

    public List<LegorderDto> getDiyLegs(List<StrategyLeg> strategyLegs) {
        log.info("Generating DIY Legs.");
        List<LegorderDto> legs = new ArrayList<>();
        for (StrategyLeg strategyLeg : strategyLegs) {
            LegorderDto leg = modelMapper.map(strategyLeg, LegorderDto.class);

        }
        return null;
    }

//    public boolean exitAll(ExitAllDto exitAllDto){
//        log.info("Received request to exit all strategies for tenant ID: "+  exitAllDto.getTenentId());
//        // has to change ar prod leval
//        List<StrategyLeg> reaultLegs = new ArrayList<>();
//        // has to change to id in exitAllDto in prod
//        Optional<AppUsers> user= appUserRepository.findById(Long.parseLong(exitAllDto.getTenentId()));
//        if (user.isPresent()) {
//            List<Signal> activeSignals = signalRepository.findAllByAppUsersIdAndStatus(user.get().getId(), "live");
//            for (Signal signal : activeSignals) {
//                if (signal.getStatus().equalsIgnoreCase("live")) {
//                    Hibernate.initialize(signal.getStrategyLeg());
//                    List<StrategyLeg> legs = signal.getStrategyLeg();
//                    Strategy strategy = signal.getStrategy();
//                    List<StrategyLeg> newLegs = createExitLegs(legs,strategy,signal);
//                    signal.setStatus("exit");
//                    signalRepository.save(signal);
//                }
//            }
//            return true;
//        }else {
//            return false;
//        }
//    }
    @Transactional
    public boolean exitAll(String clientId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            log.info("Received request to exit all strategies for user ID: {}", appUser.getAppUserId());

            List<Strategy> activeStrategies = strategyRepository.findAllByAppUserIdAndStatus(appUser.getAppUserId(), "live");
            log.info("{} active signals found for user ID: {}", activeStrategies.size(), appUser.getAppUserId());

            if (activeStrategies.isEmpty()) {
                log.info("No active signals found for user Id: {}", appUser.getAppUserId());
                return true;
            }

            for (Strategy strategy : activeStrategies) {
                if ("live".equalsIgnoreCase(strategy.getStatus())) {
                    strategy.setManualExitType(ManualExit.ENABLED.getKey());
                }
            }
            strategyRepository.saveAllAndFlush(activeStrategies);
            return true;
        } catch (UserNotFoundException e) {
            log.error("User validation failed for clientId: {}", clientId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error occurred while processing exit all request for clientId: {}", clientId, e);
            throw new RuntimeException("Unexpected error occurred for clientId: " + clientId, e);
        }
    }


    public List<StrategyLeg> createExitLegs(List<StrategyLeg> strategyLegs,Strategy strategy,Signal signal) {
        log.info("Creating exit legs for signal ID: " + signal.getId() + " with strategy ID: " + strategy.getId());
        List<StrategyLeg> newLegs = new ArrayList<>();
        for (StrategyLeg dto : strategyLegs) {
            if(!dto.getLegType().equalsIgnoreCase("open")) {
                continue;
            }
            String buySellFlag = dto.getBuySellFlag().equalsIgnoreCase("sell") ? "cover_sell" : "cover_buy";
            //  TouchlineBinaryResposne instrument = touchLineService.getTouchLine(dto.getExchangeInstrumentId().toString());
            LegorderDto newDto = new LegorderDto();
//                    newDto.setExchangeInstrumentID((long) instrument.getExchangeInstrumentId());
//                    newDto.setPrice((long) instrument.getLTP() * 1000);
            newDto.setQuantity((long) dto.getLatestUpdatedQuantity());
            newDto.setCreatedAt(Instant.now());
            newDto.setCreatedBy("bot");
            newDto.setUpdatedAt(Instant.now());
            newDto.setUpdatedBy("bot");
            newDto.setStatus("live");
            newDto.setDeleteIndicator("N");
            newDto.setBuySellFlag(buySellFlag);
            newDto.setNoOfLots(dto.getNoOfLots());
            newDto.setLegType(dto.getLegType());

            if (strategy.getStopLoss() != null) newDto.setStopLossUnitValue(strategy.getStopLoss());
            if (strategy.getTarget() != null) newDto.setTargetUnitValue(strategy.getTarget());

            newDto.setOptionType(dto.getOptionType());
            newDto.setMultiOrdersFlag("y");
            newDto.setSegment("NSEFO");

            // Map DTO to StrategyLeg
            StrategyLeg leg = modelMapper.map(newDto, StrategyLeg.class);
            leg.setAppUser(strategy.getAppUser());
            leg.setUserAdmin(strategy.getUserAdmin());
            leg.setLatestUpdatedQuantity(dto.getLatestUpdatedQuantity());
            leg.setSignal(signal);
            leg.setLegType("exit");
            leg.setId(null);

//            logger.info("Mapped Leg: " + leg);
            newLegs.add(leg);
        }
        signal.getStrategyLeg().addAll(newLegs);

         return  newLegs;
    }


    @Transactional
    public List<StrategyLeg> exitSingleStrategy(String clientId, ExitSingleStrategyDto exitSingleStrategyDto) {
        try {
            log.info("Received request to exit single strategy for signal ID: " + exitSingleStrategyDto.getSignalId());
            AppUser appUser = authService.getUserFromCLientId(clientId);
            // has to change ar prod level
//            List<StrategyLeg> reaultLegs = new ArrayList<>();
            Optional<Strategy> strategy = strategyRepository.findById(exitSingleStrategyDto.getStrategyId());

            if (strategy.isPresent()) {
                Strategy strategyObj = strategy.get();
                if (!Objects.equals(strategy.get().getAppUser().getUserId(), appUser.getUserId())) {
                    throw new Exception("cannot exit other users strategies");
                }
                if (!strategy.get().getStatus().equalsIgnoreCase(SignalStatus.EXIT.getKey())) {
                    strategyObj.setManualExitType(ManualExit.ENABLED.getKey());
                    strategyRepository.saveAndFlush(strategyObj);
                    return new ArrayList<>();
                }else
                    return null;


//                    parser.exit(strategy.get());
//                    log.info("Exit strategy deployed successfully for signal ID: " + exitSingleStrategyDto.getSignalId());
//                    return reaultLegs;
            }
            throw new RuntimeException("Something went wrong");
        }catch (Exception e) {
            // handle exceptions here
            throw new RuntimeException("Something went wrong", e);
        }
    }

    @Transactional
    public boolean standBy(String clientId, Long strategyId) {
        log.info("Received StandBy request for strategy ID: {}", strategyId);

        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            // Fetch strategy by ID
            Strategy strategy = strategyRepository.findById(strategyId)
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + strategyId));

            if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
                throw new Exception("Cannot modify other user's strategy");
            }

            // Check if strategy is already live
            if (Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus()) || Status.ERROR.getKey().equalsIgnoreCase(strategy.getStatus())) {
                return false;
            }

            boolean change = toggleStrategyStatus(strategy);
            strategyRepository.save(strategy);
            log.info("Strategy ID: {} successfully changed to standby.", strategyId);
            return change;
        } catch (StrategyNotFoundException | StrategyAlreadyLiveException e) {
            log.warn("Standby operation failed for strategy ID: {} - {}", strategyId, e.getMessage());
            throw e; // Re-throw for centralized handling
        } catch (Exception e) {
            log.error("Unexpected error during standby operation for strategy ID: {}", strategyId, e);
            throw new RuntimeException("Failed to change strategy to standby", e);
        }
    }

    public void pauseByUser(String clientId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            List<Strategy> allStrategies = strategyRepository.findAllByAppUser_Id(appUser.getAppUserId());

            for (Strategy strategy : allStrategies) {

                strategy.setHoldType("y");

                if (isTodayAnEntryDay(strategy) && !Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                    strategy.setStatus(Status.STANDBY.getKey());
                }
            }

            strategyRepository.saveAll(allStrategies);
        } catch (Exception e) {
            throw new RuntimeException("An error occurred", e);
        }
    }

    @Transactional
    public Boolean unsubscribeSingleStrategy(String clientId, Long strategyId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(strategyId)
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + strategyId));

            if (!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())) {
                throw new Exception("Cannot modify other user's strategy");
            }

            if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
//                try {
//                    parser.exit(strategy);
//                    strategy = strategyRepository.findById(strategyId)
//                            .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + strategyId));
//                } catch (Exception e) {
//                    throw new RuntimeException("unable to exit strategy, to un subscribe strategy : " + strategyId);
//                }
                return false;
            }

            if(SubscriptionStatus.START.getKey().equalsIgnoreCase(strategy.getSubscription())) {
                strategy.setSubscription(SubscriptionStatus.END.getKey());
                strategy.setStatus(Status.INACTIVE.getKey());
                strategyRepository.save(strategy);
            }
        } catch (StrategyNotFoundException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("An error occurred", e);
        }
        return true;
    }

    private boolean toggleStrategyStatus(Strategy strategy) {
        if (isTodayAnEntryDay(strategy) && STRATEGY_STATUS_RETRY_LIST.contains(strategy.getStatus())) {
            strategy.setHoldType("n");
            strategy.setStatus(Status.ACTIVE.getKey());
            strategy.setManualExitType(ManualExit.DISABLED.getKey());
            strategy.setSignalCount(0);
            return true;
        } else if (Status.ACTIVE.getKey().equals(strategy.getStatus())) {
            strategy.setHoldType("y");
            strategy.setStatus(Status.STANDBY.getKey());
            return true;
        }
        return false;
    }

    private boolean isTodayAnEntryDay(Strategy strategy) {
        List<EntryDays> entryDaysList = strategy.getEntryDetails().getEntryDays();
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        return entryDaysList.stream()
                .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(today.name()));
    }

    public DeployedErrorDTO fetchTodayErrorForStrategy(String clientId, Long strategyId ) {
        AppUser appUser = authService.getUserFromCLientId(clientId);

        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(SECONDS_TO_EOD);

        List<DeploymentErrors> deploymentErrors = deploymentErrorsRepository.
                findTodayByStrategyId(appUser.getAppUserId(), strategyId, startOfDay, endOfDay);

        DeployedErrorDTO deployedErrorDTO = new DeployedErrorDTO();
        if (deploymentErrors.isEmpty()) {
            deploymentErrors = deploymentErrorsRepository.
                    findLatest10ByStrategyId(appUser.getAppUserId(), strategyId);
        }
        deployedErrorDTO = processDeploymentErrors(deploymentErrors,strategyId, appUser);
        return deployedErrorDTO;
    }

    public DeployedErrorDTO fetchAllErrorForStrategy(String clientId, Long strategyId ){
        AppUser appUser = authService.getUserFromCLientId(clientId);

        List<DeploymentErrors> deploymentErrors = deploymentErrorsRepository.
                findByStrategyIdAndError(strategyId, appUser.getAppUserId());

        DeployedErrorDTO deployedErrorDTO = new DeployedErrorDTO();
        if (!deploymentErrors.isEmpty()){
            deployedErrorDTO = processDeploymentErrors(deploymentErrors,strategyId, appUser);
        }
        return deployedErrorDTO;
    }

    @Transactional
    public Boolean changeToLiveTrading(String clientId, Long strategyId) {
        log.info("changing strategy to live trading");
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
            if (strategyFetched.isEmpty()){
                throw new RuntimeException("No strategy found with ID: "+strategyId);
            }
            if (!appUser.equals(strategyFetched.get().getAppUser())){
                throw new RuntimeException("Selected other user strategy: "+strategyId);
            }
            Strategy strategy = strategyFetched.get();
            if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())){
                throw new RuntimeException("strategy is already live Trading: "+strategyId);
            }
            if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
                throw new RuntimeException("Cannot change Execution Mode when strategy is Live");
            }
            changeStrategyExecutionMode(strategy, ExecutionTypeMenu.LIVE_TRADING.getKey());

        return true;
    }

    @Transactional
    public void changeStrategyExecutionMode(Strategy strategy, String executionMode) {
        try {
            strategy.setExecutionType(executionMode);
            strategyRepository.saveAndFlush(strategy);
        } catch (Exception e) {
            throw new RuntimeException("Error changing execution mode for strategy " + strategy.getId(), e);
        }
    }

    @Transactional
    public Boolean changeToPaperTrading(String clientId, Long strategyId) {
        log.info("changing strategy to Forward test");
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
            if (strategyFetched.isEmpty()){
                throw new RuntimeException("No strategy found with ID: "+strategyId);
            }
            if (!appUser.equals(strategyFetched.get().getAppUser())){
                throw new RuntimeException("Selected other user strategy: "+strategyId);
            }
            Strategy strategy = strategyFetched.get();
            if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())){
                throw new RuntimeException("strategy is already Forward Test: "+strategyId);
            }
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
            throw new RuntimeException("Cannot change Execution Mode when strategy is Live");
        }
        changeStrategyExecutionMode(strategy, ExecutionTypeMenu.PAPER_TRADING.getKey());

        return true;
    }

    @Transactional
    public Boolean changeStrategyMultiplier(String clientId, OneClickDeployDto oneClickDeployDto) {
        log.info("changing strategy lo Forward test");
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyFetched = strategyRepository.findById(oneClickDeployDto.getStrategyId());
        if (strategyFetched.isEmpty()){
            throw new RuntimeException("No strategy found with ID: "+oneClickDeployDto.getStrategyId());
        }
        if (!appUser.equals(strategyFetched.get().getAppUser())){
            throw new RuntimeException("Selected other user strategy: "+oneClickDeployDto.getStrategyId());
        }
        Strategy strategy = strategyFetched.get();
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
            throw new RuntimeException("strategy is Live, unable to modify strategy Multiplier: "+oneClickDeployDto.getStrategyId());
        }

            try {
                strategy.setMultiplier(oneClickDeployDto.getMultiplier());
                strategyRepository.save(strategy);
            } catch (Exception e) {
                throw new RuntimeException("Error updating Miltiplier: " + oneClickDeployDto.getStrategyId());
            }
        return true;
    }

    @Transactional
    public int changeActiveStrategiesToStandBy(Long userId) {
        Optional<AppUser> appUsers = appUserRepository.findById(userId);
        if (appUsers.isEmpty())
            throw new RuntimeException("changeActiveStrategiesToStandBy: user not found "+userId);
        return strategyRepository.updateStrategyStatusForAppUser(appUsers.get().getId(),Status.STANDBY.getKey(),Status.ACTIVE.getKey());
    }

    public DeployedStratrgiesDto processManualEntry(String clientId, Long strategyId) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new StrategyNotFoundException("Strategy Not Found"));
        if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
            throw new RuntimeException("Cannot access other user's strategy");
        }
        if (Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
            throw new RuntimeException("This strategy is already live. Manual entry is not allowed.");
        }
        if (!Status.ERROR.getKey().equalsIgnoreCase(strategy.getStatus())) {
            throw new RuntimeException("Manual entry is only allowed for strategies in 'ERROR' state.");
        }
        strategy.setStatus(Status.ACTIVE.getKey());
        strategyRepository.save(strategy);
        return userSignalService.getActiveStrategies(clientId);
    }

    DeployedErrorDTO processDeploymentErrors(List<DeploymentErrors> deploymentErrors, Long strategyId, AppUser appUser){
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DeployedErrorDTO deployedErrorDTO = new DeployedErrorDTO();


        for (DeploymentErrors error : deploymentErrors) {
            StrategyErrorDetails strategyErrorDetails = new StrategyErrorDetails();
            strategyErrorDetails.setDescription(error.getDescription());
            strategyErrorDetails.setStatus(error.getStatus());
            strategyErrorDetails.setTimeStamp(error.getDeployedOn().atZone(zoneId).format(formatter));
            deployedErrorDTO.getStatusList().add(strategyErrorDetails);
        }
        deployedErrorDTO.setStrategyId(strategyId);
        deployedErrorDTO.setUserId(appUser.getUserId());
        return deployedErrorDTO;
    }
}
