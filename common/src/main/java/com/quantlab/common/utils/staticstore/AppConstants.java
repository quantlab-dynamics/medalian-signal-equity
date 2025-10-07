package com.quantlab.common.utils.staticstore;

import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AppConstants {
    public static final String STATUS_STAND_BY = "standby";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "available";
    public static final String STATUS_LIVE = "live";
    public static final String PLACING_ORDER = "Placing-Order";
    public static final String EXITED = "EXITED";

    public static final String STRATEGY_EXECUTION_PAPER = "PaperTrading";
    public static final String STATUS_ERROR = "error";
    public static final String API_STATUS_ERROR = "error";
    public static final String SIGNAL_STATUS_LIVE = "live";
    public static final String SIGNAL_STATUS_ACTIVE = "active";
    public static final String SIGNAL_STATUS_EXIT = "exit";

    public static final String MANUALLY_TRADED = "manually-traded";
    public static final List<String> SIGNAL_STATUS_LIVE_PAPER = new ArrayList<>(List.of(SIGNAL_STATUS_LIVE, STRATEGY_EXECUTION_PAPER));
    public static final List<String> STRATEGY_STATUS_LIVE_PAPER = new ArrayList<>(List.of(SIGNAL_STATUS_EXIT,STATUS_ACTIVE, STATUS_LIVE,STATUS_STAND_BY,STATUS_ERROR,STRATEGY_EXECUTION_PAPER));
    public static final List<String> STRATEGY_STATUS_RETRY_LIST = new ArrayList<>(List.of(SIGNAL_STATUS_EXIT,STATUS_STAND_BY));
    public static final String LEG_STATUS_TYPE_OPEN = "open";

    public static final Long AMOUNT_MULTIPLIER = 1000L;
    public static final Long GREEK_MULTIPLIER = 100000L;

    public static final int STRIKE_INTERVAL = 100;
    public static final String BUY = "Buy";
    public static final String SELL = "Sell";
    public static final String INTRADAY = "Intraday";
    public static final String POSITIONAL = "Positional" ;
    public static final String MASTERDATA = "MasterData" ;
    public static final ArrayList<String> EXPIRYVALUE = new ArrayList<>(List.of("EXP_SENSEX_IO", "EXP_NIFTY_FUTIDX", "EXP_NIFTY_OPTIDX", "EXP_BANKEX_IF", "EXP_BANKNIFTY_FUTIDX", "EXP_FINNIFTY_OPTIDX", "EXP_BANKNIFTY_OPTIDX", "EXP_FINNIFTY_FUTIDX", "EXP_BANKEX_IO", "EXP_SENSEX_IF"));
    public static final String []   ADMIN_EMAILS = {"rsriwastava@gmail.com", "bhargavagonugunta123@gmail.com", "kpdasari@gmail.com", "bhargavmoparthi@gmail.com", "venkatamohanreddygopireddy@gmail.com", "mahirworkmail@gmail.com"};
    public static final String PERCENTOFCAPITAL =  "PercentOfCapital";
    public static final String DEFAULT_UNDERLING_TYPE =  "NA";
    public static final String NIFTY =  "NIFTY";
    public static final Long AUTH_SUCCESS =  0L;
    public static final String OK_200 =  "200 OK";
    public static final String TOKEN =  "token";
    public static final String OTP_SESSION_ID =  "otpSessionId";
    public static final String MOBILE_NUMBER =  "mobileNumber";
    public static final String APP_ID =  "app-id";
    public static final String APP_KEY =  "app-key";
    public static final String CLIENT_PROFILE_ENDPOINT_UAT =  "https://publicsvc.heytorus.com/users/api/users/me";
    public static final String CLIENT_PROFILE_ENDPOINT_PROD =  "";
    public static final String CLIENT_VALIDATE_ENDPOINT_UAT =  "https://publicsvc.heytorus.com/users/api/users/me";
    public static final String CLIENT_VALIDATE_ENDPOINT_PROD =  "";
    public static final String APP_ID_VALUE =  "quantlab";
    public static final String APP_KEY_VALUE_UAT =  "mtOwyowygskpv8r6is6kddxhjanvsbsl";
    public static final String APP_KEY_VALUE_PROD =  "mOOcxatz754ndrsrryzeh5snfncpcwka";
    public static final Boolean SHOULD_AUTHENTICATE = true;
    public static final Long DEFAULT_STRATEGY_SOURCE_ID = 0L;

    public static final String SYSTEM =  "system";
    public static final Long ZERO_LONG =  0L;
    public static final Long DEFAULT_MULTIPLIER =  1L;
    public static final Long DEFAULT_MIN_MAX_VALUE = 0L;
    public static final String RUN_TIME_EXCEPTION = "Run time exception";
    public static final List<String> LIVE_ERROR = new ArrayList<>(List.of(Status.LIVE.getKey(), Status.ERROR.getKey()));
    public static final int SECONDS_TO_EOD = 86400;
    public static final String TOGGLE_TRUE = "true";
    public static final String TRADING_MODE_LIVE = "live";
    public static final String TRADING_MODE_FORWARD = "forward";
    public static final String ONBOARDING_FALSE = "n";
    public static final String XTS_COMMON_URL = "";
    public static final String LOGIN_INTERACTIVE = "/interactive/user/session";
    public static final String ERROR_PLACING_ORDER_DESCRIPTION = "Error placing order, please contact Admin. ";
    public static final String ERROR_PLACING_EXIT_ORDER_DESCRIPTION = "Error placing exit order, please contact Admin. ";
    public static final String ERROR_STRATEGY_LIVE_AFTER_EOD_DESCRIPTION = "Strategy processing error, RMS square off";
    public static final String ERROR_SIGNAL_LIVE_AFTER_EOD_DESCRIPTION = "Signal processing error, RMS square off";
    public static final String ERROR_SIGNAL_EOD_DESCRIPTION = "Intraday signal found in error, RMS Close";
    public static final String FILLED = "filled";
    public static final String HOLIDAY_REDIS_KEY = "HOLIDAY";
    public static final long NO_USER_TOKEN = 100L;

    public static final String ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND = "Error processing strategy invalid data found, please contact Admin. ";
    public static  String ACTIVE_PROFILE = "PROD";
    public static final List<String> VALID_LEG_FOR_PNL = new ArrayList<>(List.of(SIGNAL_STATUS_LIVE, MANUALLY_TRADED, LEG_STATUS_TYPE_OPEN, PLACING_ORDER));
    public static final String ERROR_SIGNAL_PLACING_EXIT = "Signal processing error, while exiting the signal, please contact Admin. ";
    public static final String FUND_LIMITS_URL = "https://tfmomsslave.torusfinancialmarkets.com/RupeeBootUE/FundLimit/GetFundLimit";
    public static final String ACCEPT_TOKEN_GENERATION = "y";
    public static final String DECLINE_TOKEN_GENERATION = "n";
    public static final String XTS_TOKEN_GENERATION_SUCCESS = "XTS Token generated successfully";
    public static final String XTS_TOKEN_GENERATION_FAILURE = "XTS Token Generation Failed";
    public static final String TR_TOKEN_VALIDATION_SUCCESS = "Token validated successfully";
    public static final String TR_TOKEN_VALIDATION_FAILURE = "XTS Token generated successfully";

    public static final Set<String> ALLOWED_TENANT_IDS = Set.of(
            "T9193259",
            "T0118221",
            "T6619107",
            "T2666356",
            "T2900133",
            "T3541184"
    );
//    ,  "T6474558",
//            "T5886325", "T0859832"

    public static final String [] BOD_EMAILS = {"rsriwastava@gmail.com", "kpdasari@gmail.com","bhargavagonugunta123@gmail.com","mahirworkmail@gmail.com"};
    public static final String [] BOD_ADMIN_EMAILS = {"rsriwastava@gmail.com", "kpdasari@gmail.com"};
    public static final String  CLIENT_NAME = "Torus";
    public static final ArrayList<String> SAFE_STATUS_LIST = new ArrayList<>(List.of("Traded", "Part-Traded", "Modified", "Pending"));
    public static final ArrayList<String> FAILED_STATUS_LIST = new ArrayList<>(List.of(StrategyStatus.REJECTED.getKey(), StrategyStatus.CANCELLED.getKey()));

    public static final ArrayList<String> FETCH_STRATEGIES_BY_STATUS = new ArrayList<>(List.of(Status.ACTIVE.getKey(), Status.LIVE.getKey(), Status.EXIT.getKey()));

}
