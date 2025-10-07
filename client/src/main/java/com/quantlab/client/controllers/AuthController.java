package com.quantlab.client.controllers;

import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.signal.dto.AuthSendDTO;
import com.quantlab.signal.dto.AuthValidatorDTO;
import com.quantlab.common.dto.ClientDetailsDTO;
import com.quantlab.signal.dto.CookieDTO;
import com.quantlab.signal.dto.redisDto.MarginResponseDTO;
import com.quantlab.signal.service.AuthService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.dto.MinMaxDto;
import com.quantlab.common.dto.WelcomeDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.quantlab.common.utils.staticstore.AppConstants.API_STATUS_ERROR;

@RestController
@RequestMapping("/auth")
public class AuthController {


    @Autowired
    private AuthService authService;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AuthSendDTO>> getClientProfile(HttpServletRequest request) throws Exception {
        try {
            // Extract token from Authorization header
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                throw new Exception("Missing or invalid Authorization header");
            }

            String token = authorizationHeader.substring(7); // Extract token after 'Bearer '
            // Validate token and fetch user details
            AuthSendDTO user = authService.fetchUserDetails(token);

            if (user != null) {
                return ResponseEntity.ok(new ApiResponse<>(
                        (user.getLoggedInToday() ? "Logging in first time today"
                                : user.getNewUser() ? "A new user is created successfully"
                                : "Existing user fetched successfully"),
                        user
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(
                        "Login Attempt Failed",
                        "Session Expired, Please login again to get new tokens",
                        null
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to check for User",
                    "Error fetching client details, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }


    @GetMapping("/userDetails")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> getUserAuthConstantsByClientId(@RequestHeader String clientId) throws Exception {
        try {
            ClientDetailsDTO user = authService.fetchProfileDataByClientId(clientId);
            return ResponseEntity.ok(new ApiResponse<>(
                    (user != null?"User Exists":"No user Found"),
                    user
            ));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to check for User",
                    "Error fetching user details",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @PostMapping("/saveProfile")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> getClientProfile(@RequestHeader String clientId ,@RequestBody MinMaxDto minMaxDto)  throws Exception {

        try {
            ClientDetailsDTO savedUser = authService.updateMinAndMaxValues(clientId, minMaxDto);

            return ResponseEntity.ok(new ApiResponse<>(
                    (savedUser != null?"Min and Max values saved successfully":"unable to find user"),
                    savedUser
            ));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to save Profile info",
                    "Error saving profile details",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @PostMapping("/welcome")
    public ResponseEntity<ApiResponse<AuthSendDTO>> welcomeProfile(@RequestBody WelcomeDto welcomeDto , HttpServletRequest request)  throws Exception {
        try {
            Boolean saved = authService.updateLoginInfo(welcomeDto,request);
            Boolean acknowledged = authService.handleWelcomeAcknowledgement(welcomeDto, request);
            AuthSendDTO dto = authService.welcomeResponse(welcomeDto.getClientId());

            return ResponseEntity.ok(new ApiResponse<>(
                    (saved?" Welcome to algoXpert":""),
                    dto
            ));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch Welcome pop up",
                    "unable to login with given details, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @GetMapping("/margin")
    public ResponseEntity<ApiResponse<MarginResponseDTO>> marginDetails(@RequestHeader String clientId, HttpServletRequest request) {
        try {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                throw new RuntimeException("Missing or invalid Authorization header");
            }

            String token = authorizationHeader.substring(7);
            MarginResponseDTO dto = authService.fetchMargin(token, clientId);
            return ResponseEntity.ok(new ApiResponse<>(
                    "Margin fetched successfully",
                    dto
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch margin details",
                    "unable to fetch margin details, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }

    }
}
