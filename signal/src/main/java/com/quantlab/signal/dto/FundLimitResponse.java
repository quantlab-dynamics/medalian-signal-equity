package com.quantlab.signal.dto;

import lombok.Data;

import java.util.List;

@Data
public class FundLimitResponse {
    private List<FundLimitData> data;
    private String error_code;
    private String message;
    private String iv;
    private String status;

    @Data
    public static class FundLimitData {
        private double mtf_available_balance;
        private String limit_type;
        private double gross_holding_value;
        private double bank_clear_balance;
        private double adhoc_limit;
        private double sod_unclear_bal;
        private double collaterals;
        private double mtf_collateral;
        private double amount_utilized;
        private double bank_unclear_balance;
        private double opt_premium;
        private double available_balance;
        private double withdrawal_balance;
        private double total_balance;
        private double mtf_utilize;
        private double mtm_combined;
        private double pay_out_amt;
        private double realised_profits;
        private double opt_premium_com;
        private String segment;
        private double limit_sod;
        private double receivables;
        private double opt_buy_premium_utilize;
        private double peak_margin;
    }
}
