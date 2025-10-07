package com.quantlab.client.filter;

import com.quantlab.common.dto.ApiResponseDTO;
import com.quantlab.common.dto.ClientDetailsDTO;
import com.quantlab.signal.service.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static com.quantlab.common.utils.staticstore.AppConstants.CLIENT_VALIDATE_ENDPOINT_UAT;

@Component
public class ValidationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ValidationFilter.class);
    private final Set<String> excludedApis = Set.of("/ql/ws/open-positions-data");
    @Autowired
    public AuthService authService;

    @Value("${validation_url}")
    private String validationUrl;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isWebSocketHandshake(httpRequest) || isExcludedApi(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        setCorsHeaders(httpResponse);

        // Extract the Authorization header
        String authorizationHeader = httpRequest.getHeader("Authorization");

        // Extract token
        String token = authorizationHeader.substring(7);

        try {
            ApiResponseDTO apiResponse = authService.validateUser(token, CLIENT_VALIDATE_ENDPOINT_UAT);

            if (apiResponse == null) {
                throw new Exception("Invalid token or user");
            }
            ClientDetailsDTO clientDetailsDTO = apiResponse.processResponse();

            // Extract clientId from the response
            String clientId = clientDetailsDTO.getClientId();

            // Wrap request to add userId header
            HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getHeader(String name) {
                    if ("clientId".equalsIgnoreCase(name)) {
                        return clientId;
                    }
                    return super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(String name) {
                    if ("clientId".equalsIgnoreCase(name)) {
                        return Collections.enumeration(List.of(clientId));
                    }
                    return super.getHeaders(name);
                }

                @Override
                public Enumeration<String> getHeaderNames() {
                    List<String> headerNames = Collections.list(super.getHeaderNames());
                    headerNames.add("clientId");
                    return Collections.enumeration(headerNames);
                }
            };

            chain.doFilter(wrappedRequest, response);

        } catch (Exception e) {
            logger.error("User validation failed: {}", e.getMessage());
            rejectRequest(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "11209", e.getMessage());
        }
    }

    @Override
    public void destroy() {
    }

    private boolean isWebSocketHandshake(HttpServletRequest request) {
        return "websocket".equalsIgnoreCase(request.getHeader("Upgrade"));
    }

    private boolean isExcludedApi(String requestUri) {
        return excludedApis.contains(requestUri);
    }

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, userId");
    }

    private void rejectRequest(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        String jsonResponse = String.format("{\"errorCode\": \"%s\", \"errorMessage\": \"%s\"}",
                errorCode, message.replace("\"", "\\\""));
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
