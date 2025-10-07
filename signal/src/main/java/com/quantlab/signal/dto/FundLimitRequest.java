package com.quantlab.signal.dto;

import lombok.Data;

@Data
public class FundLimitRequest {
    private String entity_id;
    private String source;
    private String token_id;
    private String iv;
    private RequestData data;

    @Data
    public static class RequestData {
        private String client_id;
    }
}
