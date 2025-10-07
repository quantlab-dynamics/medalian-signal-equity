package com.quantlab.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quantlab.common.dto.ClientDetailsDTO;
import lombok.Data;

@Data
public class ApiResponseDTO {
    private Long code;
    private String message;
    private UserDataResponseDTO data;

    public ClientDetailsDTO processResponse() {
        if (data != null && data.getUser() != null) {
            return data.processData();
        }
        return null;
    }
}

@Data
class UserDataResponseDTO {
    @JsonProperty("user")
    private UserResponseDTO user;

    public ClientDetailsDTO processData() {
        ClientDetailsDTO clientDetailsDTO = new ClientDetailsDTO();

        clientDetailsDTO.setClientId(user.getUniqueClientCode());
        clientDetailsDTO.setClientName(user.getEmail()); // Assuming email is used as client name
        clientDetailsDTO.setMobileNo(user.getPhone());
        clientDetailsDTO.setEmailId(user.getEmail());

        // Set default values for XTS-related fields if they don't exist in the new structure
        clientDetailsDTO.setXtsClient(false);
        clientDetailsDTO.setXTSAppKey(null);
        clientDetailsDTO.setXTSSecretKey(null);

        return clientDetailsDTO;
    }
}

@Data
class UserResponseDTO {
    @JsonProperty("ID")
    private Long id;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("UpdatedAt")
    private String updatedAt;

    @JsonProperty("deletedAt")
    private String deletedAt;

    @JsonProperty("email")
    private String email;

    @JsonProperty("emailVerified")
    private boolean emailVerified;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("phoneVerified")
    private boolean phoneVerified;

    @JsonProperty("referral")
    private String referral;

    @JsonProperty("source")
    private String source;

    @JsonProperty("uniqueCientCode") // Map the misspelled JSON field
    private String uniqueClientCode; // Correct field name in Java

    @JsonProperty("onboarding")
    private String onboarding;

    @JsonProperty("digiGoldOnboarding")
    private String digiGoldOnboarding;

    @JsonProperty("emailModifications")
    private String emailModifications;

    @JsonProperty("phoneModifications")
    private String phoneModifications;

    @JsonProperty("bankAccountModifications")
    private String bankAccountModifications;

    @JsonProperty("segmentModifications")
    private String segmentModifications;

    @JsonProperty("accountClosures")
    private String accountClosures;

    @JsonProperty("utmSource")
    private String utmSource;

    @JsonProperty("utmMedium")
    private String utmMedium;

    @JsonProperty("utmCampaign")
    private String utmCampaign;

    @JsonProperty("utmTerm")
    private String utmTerm;

    @JsonProperty("utmContent")
    private String utmContent;

    @JsonProperty("landingPage")
    private String landingPage;

    @JsonProperty("formName")
    private String formName;

    @JsonProperty("plan")
    private String plan;

    @JsonProperty("ssfb_cid")
    private String ssfbCid;

    @JsonProperty("journeyType")
    private String journeyType;

    @JsonProperty("vkyc_status")
    private String vkycStatus;

    @JsonProperty("bank_account_type")
    private String bankAccountType;

    @JsonProperty("bank_auditor_remark")
    private String bankAuditorRemark;

    public boolean getXtsClient() {
        return false; // Placeholder for compatibility
    }
}
