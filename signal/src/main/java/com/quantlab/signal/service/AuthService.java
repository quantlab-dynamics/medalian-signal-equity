package com.quantlab.signal.service;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.quantlab.common.dao.StrategyIdAndSourceIdDAO;
import com.quantlab.common.dto.ApiResponseDTO;
import com.quantlab.common.dto.ClientDetailsDTO;
import com.quantlab.common.dto.MinMaxDto;
import com.quantlab.common.dto.WelcomeDto;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.UserNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.UserType;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.TradingMode;
import com.quantlab.signal.dto.*;
import com.quantlab.signal.dto.redisDto.InterActiveTokensDTO;
import com.quantlab.signal.dto.redisDto.MarginResponseDTO;
import com.quantlab.signal.service.redisService.InterActiveTokensRepository;
import com.quantlab.signal.sheduler.BodSchedule;
import com.quantlab.signal.utils.AuthUtils;
import com.quantlab.signal.utils.TestMail;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.common.utils.staticstore.AppConstants.DECLINE_TOKEN_GENERATION;
import static com.quantlab.signal.utils.staticdata.StaticStore.EXCEPTION_DATE;

@Service
public class AuthService {
    private static final Logger logger = LogManager.getLogger(AuthService.class);

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    userRoleRepository userRoleRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    TokenLogInfoRepository tokenLogInfoRepository;


    @Autowired
    StrategyCategoryRepository strategyCategoryRepository;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    AppUserLogInfoRepository appUserLogInfoRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    UIConstantsRepository uiConstantsRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    BodSchedule bodSchedule;

    @Autowired
    XtsService xtsService;

    @Autowired
    InterActiveTokensRepository interActiveTokensRepository;


    @Autowired
    TestMail testMail;

    @Value("${profile_url}")
    private String profileValidationUrl;

    public ApiResponseDTO validateUser(String token, String endpointURL) throws Exception {
        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(endpointURL, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new Exception("Invalid response from validation endpoint: " + response.getStatusCode());
        }

        // Parse the response
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ApiResponseDTO apiResponseDTO = objectMapper.readValue(response.getBody(), ApiResponseDTO.class);

        return apiResponseDTO;
    }


    @Transactional
    public AuthSendDTO fetchUserDetails(String token) {
        UserAuthConstants selectedClient;
        LocalDate today = LocalDate.now();
        AuthSendDTO userAuth =  new AuthSendDTO();
        try {
            ApiResponseDTO apiResponseDTO = validateUser(token, profileValidationUrl);
            ClientDetailsDTO clientDetailsDTO = apiResponseDTO.processResponse();
            if (clientDetailsDTO != null) {
                UIConstants noUserTokenUiConstants = uiConstantsRepository.findByCode(NO_USER_TOKEN);
                selectedClient = checkUser(clientDetailsDTO.getClientId());
                if (selectedClient != null) {
                    AppUser user = selectedClient.getAppUser();
                    if (user == null) {
                        throw new UserNotFoundException("User Not Found");
                    }
//                    if (user.getTenentId() == null || user.getTenentId().isEmpty()) {
//                        user.setTenentId(cookieDTO.getClientId());
//                        appUserRepository.save(user);
//                    }
                    userAuth.setNewUser(false);
                    userAuth.setLoggedInToday(selectedClient.getLastWelcomeAckTime() == null || !selectedClient.getLastWelcomeAckTime().toLocalDate().equals(today));
                    selectedClient.setXtsAppKey(clientDetailsDTO.getXTSAppKey());
                    selectedClient.setXtsSecretKey(clientDetailsDTO.getXTSSecretKey());
                    selectedClient.setEmailId(clientDetailsDTO.getEmailId());
                    selectedClient.setName(clientDetailsDTO.getClientName());
                    selectedClient.setAddress(clientDetailsDTO.getAddress());
                    selectedClient.setXtsClient(clientDetailsDTO.getXtsClient());
                    selectedClient.setToken(token);

                    existingUserToCreteNewStrategy(selectedClient.getClientId());
                    selectedClient.setPreviousLoggedinTime(LocalDateTime.now());
//                    selectedClient.setToken(cookieDTO.getToken());
//                    selectedClient.setMobileNumber(cookieDTO.getMobileNumber());
                    userAuthConstantsRepository.save(selectedClient);
                } else {
                    userAuth.setNewUser(true);
                    userAuth.setLoggedInToday(false);
                    selectedClient = new UserAuthConstants();
                    selectedClient.setClientId(clientDetailsDTO.getClientId());
                    selectedClient.setEmailId(clientDetailsDTO.getEmailId());
                    selectedClient.setName(clientDetailsDTO.getClientName());
                    selectedClient.setAddress(clientDetailsDTO.getAddress());
                    selectedClient.setXtsClient(clientDetailsDTO.getXtsClient());
                    selectedClient.setXtsAppKey(clientDetailsDTO.getXTSAppKey());
                    selectedClient.setXtsSecretKey(clientDetailsDTO.getXTSSecretKey());
                    selectedClient.setPreviousLoggedinTime(LocalDateTime.now());
                    selectedClient.setMobileNumber(clientDetailsDTO.getMobileNo());
                    selectedClient.setToken(token);
                    selectedClient.setMaxLoss(DEFAULT_MIN_MAX_VALUE);
                    selectedClient.setMinProfit(DEFAULT_MIN_MAX_VALUE);
                    selectedClient.setUserTradingMode(TradingMode.FORWARD.getKey());
                    selectedClient = saveNewUserData(selectedClient);
                }
                userAuth.setUserId(selectedClient.getAppUser().getId());
                userAuth.setClientId(selectedClient.getClientId());
                userAuth.setIsLive(TradingMode.LIVE.getKey().equalsIgnoreCase(selectedClient.getUserTradingMode()));
                if (!TradingMode.LIVE.getKey().equalsIgnoreCase(selectedClient.getUserTradingMode())) {
                    userAuth.setBannerContent(noUserTokenUiConstants.getDescription());
                }
            }
        } catch (Exception e) {
            if (e.getMessage().contains(OK_200))
                return null;
            logger.error("error fetching user details {}", e.getMessage());
            throw new RuntimeException("Error fetching user details", e);
        }
        return userAuth;

    }

    public void existingUserToCreteNewStrategy(String clientId) {
        try {
            List<StrategyIdAndSourceIdDAO> strategyIdAndSourceIdDAOS = strategyRepository.findAllStrategyIDsAndSourceId(clientId);
            List<StrategyIdAndSourceIdDAO> strategyIdsAdmin= strategyRepository.findAllStrategyIDsAndSourceIdByAdminId();

            Set<Long> list2Ids = strategyIdAndSourceIdDAOS.stream()
                    .map(StrategyIdAndSourceIdDAO::getSourceId)
                    .collect(Collectors.toSet());

            List<StrategyIdAndSourceIdDAO> adminResultList = strategyIdsAdmin.stream()
                    .filter(obj -> !list2Ids.contains(obj.getId()))
                    .toList();
            if (!adminResultList.isEmpty()) {
                AppUser appUser = appUserRepository.findByTenentId(clientId.toString()).orElseThrow(() -> new UserNotFoundException("User Not Found"));
                setDefaultStrategys(appUser, true, adminResultList.stream()
                        .map(StrategyIdAndSourceIdDAO::getId)
                        .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            logger.error("Error checking existing user: {}", e.getMessage());
        }
    }

    public UserAuthConstants saveNewUserData(UserAuthConstants selectedClient) {
        try {
            AppUser appUser = createAppUser(selectedClient);
            appUser.setUserName(selectedClient.getName());
            appUser = appUserRepository.save(appUser);
            selectedClient.setAppUser(appUser);
            selectedClient = userAuthConstantsRepository.save(selectedClient);
            setDefaultStrategys(appUser, false, new ArrayList<Long>());
        } catch (Exception e) {
            logger.error("unable to save client details = {}", e.getMessage());
        }
        return selectedClient;
    }

    private void setDefaultStrategys(AppUser appUser, boolean partial, List<Long>strategyIds) {

        try {
            List<Strategy> defaultStrategys = new ArrayList<>();
            if (partial) {
                defaultStrategys = strategyRepository.findByIdIn(strategyIds);
            } else {
                defaultStrategys = strategyRepository.findDefaultStrategys();
            }
            Hibernate.initialize(defaultStrategys);
            // need to discuss what should be the default admin id for each user
            UserAdmin userAdmin = adminRepository.findById(1);
            List<EntryDays> allEntryDays = entryDaysRespository.findAll();
//            StrategyCategory sc = strategyCategoryRepository.getReferenceById(1l);
            for (Strategy fetchedStrategy: defaultStrategys){

                Hibernate.initialize(fetchedStrategy.getEntryDetails());
                Hibernate.initialize(fetchedStrategy.getExitDetails());
                List<StrategyLeg> newStrategyLegs = new ArrayList<>();

                Strategy savingStrategy = new Strategy();
                EntryDetails entryDetails = new EntryDetails();
                ExitDetails exitDetails = new ExitDetails();

                BeanUtils.copyProperties(fetchedStrategy, savingStrategy);
                BeanUtils.copyProperties(fetchedStrategy.getEntryDetails(), entryDetails);
                BeanUtils.copyProperties(fetchedStrategy.getExitDetails(), exitDetails);

                savingStrategy.setId(null);
                savingStrategy.setSignals(null);
                savingStrategy.setAppUser(appUser);
                savingStrategy.setUserAdmin(userAdmin);
                savingStrategy.setStrategyLeg(null);
                savingStrategy.setStatus(com.quantlab.common.utils.staticstore.dropdownutils.Status.INACTIVE.getKey());
                savingStrategy.setSourceId(fetchedStrategy.getId());
                entryDetails.setId(null);
                exitDetails.setId(null);
                entryDetails.setStrategy(savingStrategy);
                exitDetails.setStrategy(savingStrategy);
                savingStrategy.setEntryDetails(entryDetails);
                savingStrategy.setExitDetails(exitDetails);
                savingStrategy.setSubscription("N");
//                savingStrategy.setStrategyCategory(sc);
                for (StrategyLeg leg : fetchedStrategy.getStrategyLeg()){
                    StrategyLeg copyLeg = new StrategyLeg();
                    BeanUtils.copyProperties(leg, copyLeg);
                    copyLeg.setId(null);
                    copyLeg.setAppUser(appUser);
                    copyLeg.setUserAdmin(appUser.getAdmin());
                    copyLeg.setStatus(Status.ACTIVE.getKey());
                    copyLeg.setStrategy(savingStrategy);
                    newStrategyLegs.add(copyLeg);
                }
                savingStrategy.setStrategyLeg(newStrategyLegs);
                savingStrategy.setSignalCount(0);
                savingStrategy = strategyRepository.save(savingStrategy);
                entryDetails.setStrategy(savingStrategy);
                entryDetails.setEntryDays(allEntryDays);
            }

        } catch (Exception e) {
            logger.error("error creating default Strategies for user {}, {}", appUser.getUserId(), e.getMessage());
        }
    }

    private UserAuthConstants checkUser(String clientId) {
        try {
            Optional<UserAuthConstants> fetchedClient = userAuthConstantsRepository.findByClientId(clientId);
            if (fetchedClient.isPresent()) {
                return fetchedClient.get();
            }
        } catch (Exception e) {
            logger.error("unable to check clientID in DB{}", e.getMessage());
            throw new RuntimeException("unable to check clientID in DB", e);
        }
        return null;
    }

    public AppUser createAppUser(UserAuthConstants selectedClient) {

        try {
            AppUser appUser = new AppUser();
            //this is for Admin we need to change based on the role in future
            UserRole userRole = userRoleRepository.getReferenceById(1L);
            UserAdmin userAdmin= adminRepository.findById(1);
            // Set mandatory fields with provided values
            appUser.setCreatedBy(SYSTEM);  // Default "system" if createdBy is null
            appUser.setUpdatedBy(SYSTEM);  // Default updatedBy to createdBy if null

            appUser.setUserName(selectedClient.getName());
            appUser.setStatus(Status.ACTIVE.getKey());  // Default status to "active" if null
            appUser.setInvestment(ZERO_LONG);  // Default investment to 0 if null
            appUser.setCurrentValue(ZERO_LONG);  // Default currentValue to 0 if null
            appUser.setTodayProfitLoss(ZERO_LONG);  // Default to 0 if null
            appUser.setOverallProfitLoss(ZERO_LONG);  // Default to 0 if null
            appUser.setUserRole(userRole);
            appUser.setAdmin(userAdmin);
            appUser.setTenentId(selectedClient.getClientId());
            return appUser;
        } catch (Exception e) {
            logger.error("unable to create AppUser {}", e.getMessage());
            throw new RuntimeException("unable to create AppUser", e);
        }
    }

    public ClientDetailsDTO fetchProfileDataByClientId(String clientId){
        try {
            return userAuthConstantsRepository.findByClientId(clientId)
                    .map(this::createClientDTO)
                    .orElse(new ClientDetailsDTO());
        }catch (Exception e){
            throw new RuntimeException("Something went wrong", e);
        }
    }

    public ClientDetailsDTO updateMinAndMaxValues(String clientId, MinMaxDto minMaxDto){

        try{
            Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
            if (userAuthConstantsOptional.isEmpty()) {
                throw new Exception("client info not found for clientId : " + clientId);
            }
            UserAuthConstants userAuthConstants = userAuthConstantsOptional.get();
            if (minMaxDto.getMinProfit() != null)
                userAuthConstants.setMinProfit((long) (minMaxDto.getMinProfit()*AMOUNT_MULTIPLIER));

            if (minMaxDto.getMaxLoss() != null)
                userAuthConstants.setMaxLoss((long) (minMaxDto.getMaxLoss()*AMOUNT_MULTIPLIER));

            userAuthConstantsRepository.save(userAuthConstants);
            return createClientDTO(userAuthConstants);
        } catch (RuntimeException e) {
            logger.error("unable to save min profit = "+minMaxDto.getMinProfit()+", maxLoss = "+minMaxDto.getMaxLoss());

            throw new RuntimeException("unable to store the MaxLoss and MinProfit data");
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong", e);
        }
    }

    @Transactional
    public Boolean updateLoginInfo(WelcomeDto welcomeDto , HttpServletRequest request){

        try{
            String authToken = "";
            String userAgent = request.getHeader("User-Agent");
            String machineIp = getClientIp(request);

            Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(welcomeDto.getClientId());
            if (userAuthConstantsOptional.isEmpty()) {
                throw new Exception("client info not found for clientId : " + welcomeDto.getClientId());
            }
            UserAuthConstants userAuthConstants = userAuthConstantsOptional.get();
                AppUserLogInfo appUserLogInfo = new AppUserLogInfo();
                Hibernate.initialize(userAuthConstants.getAppUser());
                appUserLogInfo.setAppUser(userAuthConstants.getAppUser());
                appUserLogInfo.setLoggedinTime(Instant.now());
                appUserLogInfo.setMechineId(machineIp);
                appUserLogInfo.setUserAgent(userAgent);
                appUserLogInfoRepository.save(appUserLogInfo);


                return true;
        } catch (RuntimeException e) {
//            e.printStackTrace();
            throw new RuntimeException("unable to store the User Login data data" + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong", e);
        }
    }

    @Transactional
    public Boolean handleWelcomeAcknowledgement(WelcomeDto welcomeDto, HttpServletRequest request) {
        String clientId = welcomeDto.getClientId();
        Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
        if (userAuthConstantsOptional.isEmpty()) {
            return false;
        }

        UserAuthConstants userAuthConstants = userAuthConstantsOptional.get();
        if (!welcomeDto.isTermsConditions()) {
            userAuthConstants.setUserTradingMode(TRADING_MODE_FORWARD);
            userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
            TokenLogInfo tokenLogInfo = new TokenLogInfo();
            tokenLogInfo.setAcknowledgementType(DECLINE_TOKEN_GENERATION);
            tokenLogInfo.setMachineId(getClientIp(request));
            tokenLogInfo.setAppUser(userAuthConstants.getAppUser());
            tokenLogInfo.setUserAgent(request.getHeader("User-Agent"));
            tokenLogInfo.setWelcomeAcknowledgedTime(Instant.now());
            tokenLogInfoRepository.save(tokenLogInfo);
            userAuthConstantsRepository.save(userAuthConstants);
            return true;
        }
        else {
            storeInteractiveTokensInRedis(userAuthConstants);
            userAuthConstants.setUserTradingMode(TradingMode.LIVE.getKey());
            userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
            logTokenEvent(
                    userAuthConstants,
                    request,
                    UserType.TR.getKey(),
                    ACCEPT_TOKEN_GENERATION,
                    userAuthConstants.getToken(),
                    TR_TOKEN_VALIDATION_SUCCESS,
                    true
            );
            userAuthConstantsRepository.save(userAuthConstants);
            return true;
        }
    }

    private void logTokenEvent(UserAuthConstants userAuthConstants,
                               HttpServletRequest request,
                               String tokenType,
                               String acknowledgementType,
                               String tradeToken,
                               String remarks,
                               boolean tokenGenerated) {

        TokenLogInfo tokenLogInfo = new TokenLogInfo();
        tokenLogInfo.setTokenType(tokenType);
        tokenLogInfo.setAcknowledgementType(acknowledgementType);
        tokenLogInfo.setTradeToken(tradeToken);
        tokenLogInfo.setMachineId(getClientIp(request));
        tokenLogInfo.setAppUser(userAuthConstants.getAppUser());
        tokenLogInfo.setUserAgent(request.getHeader("User-Agent"));
        tokenLogInfo.setWelcomeAcknowledgedTime(Instant.now());

        if (tokenGenerated) {
            tokenLogInfo.setTokenGeneratedTime(Instant.now());
        }

        tokenLogInfo.setRemarks(remarks);

        tokenLogInfoRepository.save(tokenLogInfo);
    }

    public void storeInteractiveTokensInRedis(UserAuthConstants userAuthConstants) {
        try {
            InterActiveTokensDTO interActiveTokensDTO = new InterActiveTokensDTO();
            interActiveTokensDTO.setClientId(userAuthConstants.getClientId());
            interActiveTokensDTO.setToken(userAuthConstants.getToken());

            if (interActiveTokensDTO.getToken() != null && interActiveTokensDTO.getClientId() != null) {
                interActiveTokensRepository.save(interActiveTokensDTO.getClientId(), interActiveTokensDTO);
                logger.info("Stored  token in Redis for clientId: {}", interActiveTokensDTO.getClientId());
            } else {
                logger.warn("token is null or empty for clientId: {}", interActiveTokensDTO.getClientId());
            }
        } catch (Exception e) {
            logger.error("Error storing XTS token in Redis: {}", e.getMessage());
        }
    }

    public AuthSendDTO welcomeResponse(String clientId) {
        AuthSendDTO dto = new AuthSendDTO();
        Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
        UIConstants noUserTokenUiConstants = uiConstantsRepository.findByCode(NO_USER_TOKEN);
        if (userAuthConstantsOptional.isEmpty()) {
            throw new RuntimeException("client info not found for clientId : " + clientId);
        }
        UserAuthConstants user = userAuthConstantsOptional.get();
        dto.setUserId(user.getAppUser().getId());
        dto.setNewUser(false);
        dto.setLoggedInToday(false);
        dto.setIsLive(TradingMode.LIVE.getKey().equalsIgnoreCase(user.getUserTradingMode()));
        if (!TradingMode.LIVE.getKey().equalsIgnoreCase(user.getUserTradingMode())) {
            dto.setBannerContent(noUserTokenUiConstants.getDescription());
        }
        return dto;
    }

    public void setUserTradingModeToForward(String clientId) {
        UserAuthConstants userAuthConstants = userAuthConstantsRepository.findByClientId(clientId)
                .orElseThrow(() -> new UserNotFoundException("User Not Found"));
        userAuthConstants.setUserTradingMode(TRADING_MODE_FORWARD);
        userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
        userAuthConstantsRepository.save(userAuthConstants);
    }


    public static String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    public AppUser getUserFromCLientId(String clientId) {
        Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
        if(userAuthConstantsOptional.isEmpty()) {
            logger.error("User with clientId - " + clientId + " not found ");
            throw new UserNotFoundException("User with clientId - " + clientId + " not found");
        }
        return userAuthConstantsOptional.get().getAppUser();
    }

    public MarginResponseDTO fetchMargin(String token, String clientId) {
        // Prepare request headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        FundLimitRequest requestBody = new FundLimitRequest();
        requestBody.setEntity_id(clientId);
        requestBody.setSource("M");

        FundLimitRequest.RequestData data = new FundLimitRequest.RequestData();
        data.setClient_id(clientId);
        requestBody.setData(data);

        HttpEntity<FundLimitRequest> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<FundLimitResponse> response = restTemplate.exchange(
                    FUND_LIMITS_URL,
                    HttpMethod.POST,
                    entity,
                    FundLimitResponse.class
            );

            FundLimitResponse fundLimitResponse = response.getBody();
            if (fundLimitResponse == null || !"success".equals(fundLimitResponse.getStatus()) || fundLimitResponse.getData() == null || fundLimitResponse.getData().isEmpty()) {
                throw new RuntimeException("Failed to fetch margin details: " + (fundLimitResponse != null ? fundLimitResponse.getMessage() : "Empty response"));
            }

            FundLimitResponse.FundLimitData fundLimitData = fundLimitResponse.getData().get(0);
            MarginResponseDTO marginResponseDTO = new MarginResponseDTO();
            marginResponseDTO.setAvailableMargin(fundLimitData.getAvailable_balance());
            marginResponseDTO.setUtilizedMargin(fundLimitData.getAmount_utilized());
            marginResponseDTO.setTotalBalance(fundLimitData.getTotal_balance());

            return marginResponseDTO;
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching margin details: " + e.getMessage(), e);
        }
    }

    ClientDetailsDTO createClientDTO(UserAuthConstants userAuthConstants){
        ClientDetailsDTO clientDetailsDTO = new ClientDetailsDTO();
        clientDetailsDTO.setClientId(userAuthConstants.getClientId());
            clientDetailsDTO.setUserId(userAuthConstants.getAppUser().getUserId());
            clientDetailsDTO.setClientName(userAuthConstants.getName());
            clientDetailsDTO.setEmailId(userAuthConstants.getEmailId());
            clientDetailsDTO.setMobileNo(userAuthConstants.getMobileNumber());
            clientDetailsDTO.setAddress(userAuthConstants.getAddress());
            clientDetailsDTO.setXtsClient(userAuthConstants.getXtsClient());
            clientDetailsDTO.setMaxLoss((userAuthConstants.getMaxLoss()/ (double)AMOUNT_MULTIPLIER));
            clientDetailsDTO.setMinProfit(userAuthConstants.getMinProfit()/ (double) AMOUNT_MULTIPLIER);
            clientDetailsDTO.setXTSAppKey(LocalTime.now().toString());
            return clientDetailsDTO;
    }

    public Boolean setExceptionDate(String clientId,String dateString) {

        if (!Objects.equals(clientId, "1052888"))
            throw new RuntimeException("user is not having Admin access to set Exception Date");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        EXCEPTION_DATE = LocalDate.parse(dateString, formatter);
        bodSchedule.bodScheduler();

        return true;
    }
}
