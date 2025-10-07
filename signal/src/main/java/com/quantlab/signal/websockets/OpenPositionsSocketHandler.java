//package com.quantlab.signal.websockets;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.quantlab.common.dto.ApiResponseDTO;
//import com.quantlab.signal.dto.CookieDTO;
//import com.quantlab.signal.service.AuthService;
//import com.quantlab.signal.service.SocketDataProcessorService;
//import lombok.Data;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Objects;
//import java.util.concurrent.CopyOnWriteArrayList;
//
//import static com.quantlab.common.utils.staticstore.AppConstants.*;
//import static com.quantlab.signal.service.SocketDataProcessorService.subscribedAppUsers;
//
//@Component
//public class OpenPositionsSocketHandler extends TextWebSocketHandler {
//
//    private static final Logger logger = LogManager.getLogger(OpenPositionsSocketHandler.class);
//    private static Map<String, CopyOnWriteArrayList<WebSocketSession>> userSessions = new HashMap<>();
////    static int count =0;
//
//    @Autowired
//    SocketDataProcessorService socketDataProcessorService;
//
//    ObjectMapper objectMapper = new ObjectMapper();
//
//    @Autowired
//    AuthService authService;
//
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException, InterruptedException {
//        try {
//
//            String sessionKey=validateUser(message.getPayload());
//            logger.info("SessionID = "+session.getId());
//            String userIdPayload = message.getPayload();
//
////            if (!userIdPayload.isEmpty()) {
////                UserPayload userPayload = objectMapper.readValue(userIdPayload, UserPayload.class);
////                sessionKey = userPayload.getUser();
////            }
//            if (userSessions.containsKey(sessionKey)) {
//                CopyOnWriteArrayList<WebSocketSession> sessionsLists = userSessions.get(sessionKey);
//                sessionsLists.add(session);
//                userSessions.put(sessionKey,sessionsLists);
//            }else {
//                CopyOnWriteArrayList<WebSocketSession> sessionsLists = new CopyOnWriteArrayList<>();
//                sessionsLists.add(session);
//                userSessions.put(sessionKey,  sessionsLists);
//            }
//            socketDataProcessorService.startProcessing(sessionKey);
////            redisProcessingMultiThreadingService.startThreadProcessing(sessionKey);
//        } catch (Exception e) {
//            logger.error("failed at afterConnectionEstablished "+e.getMessage());
//        }
//    }
//
//    private String validateUser(String response){
//
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            CookieDTO cookieDTO = objectMapper.readValue(response, CookieDTO.class);
//
//            if (!SHOULD_AUTHENTICATE)
//                return cookieDTO.getUserId();
//
//            String url = CLIENT_PROFILE_ENDPOINT + cookieDTO.getClientId();
//            // headers
//            HttpHeaders headers = new HttpHeaders();
//            headers.set(TOKEN, cookieDTO.getToken());
//            headers.set(OTP_SESSION_ID, cookieDTO.getOtpSessionId());
//            headers.set(MOBILE_NUMBER, cookieDTO.getMobileNumber());
//            headers.set(APP_ID, APP_ID_VALUE);
//            headers.set(APP_KEY, APP_KEY_VALUE);
//            HttpEntity<String> entity = new HttpEntity<>(headers);
//            RestTemplate restTemplate = new RestTemplate();
//            ResponseEntity<String> validationResponse = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
//            ApiResponseDTO apiResponseDTO = objectMapper.readValue(validationResponse.getBody(), ApiResponseDTO.class);
//
//            if (apiResponseDTO.getCode().equals(AUTH_SUCCESS)) {
//                return cookieDTO.getUserId();
//            }
//            throw new Exception(validationResponse.toString());
//        } catch (Exception e) {
//
//            throw new RuntimeException(e.getMessage());
//        }
//    }
////
////    public void afterConnectionEstablished(WebSocketSession session) throws IOException, InterruptedException {
////        try {
////            session.sendMessage(new TextMessage("hello World"));
////            Long sessionKey=1L;
////            logger.info("SessionID = "+session.getId());
////            userSessions.put(sessionKey, session);
////            socketDataProcessorService.startProcessing(sessionKey);
////
////
////        } catch (Exception e) {
////            logger.error("failed at afterConnectionEstablished "+e.getMessage());
////        }
////
////    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
//        logger.info("inside afterConnectionClosed SessionKey " + session.getId());
//    }
//
//    public void sendOpenPositionsToUser(String sessionKey, Object marketData) {
//
//        if (sessionExists(sessionKey)) {
//            try {
//                CopyOnWriteArrayList<WebSocketSession> sessionList = userSessions.get(sessionKey);
//                for (WebSocketSession session: sessionList) {
//                    String data = objectMapper.writeValueAsString(marketData);
//                    session.sendMessage(new TextMessage(data));
//                }
//                Thread.sleep(50);
//            } catch (Exception e) {
//                logger.error("failed to sendOpenPositionsToUser "+e.getMessage());
//            }
//        }
//    }
//
//    public void closeConnection(String sessionKey, WebSocketSession currentWebSocketSession) {
//        logger.info("inside closeConnection SessionKey " + sessionKey);
//
//        try {
//                CloseStatus status = new CloseStatus(4000, "socket disconnected");
//                CopyOnWriteArrayList<WebSocketSession> sessionList = userSessions.get(sessionKey);
//                if (currentWebSocketSession != null && !currentWebSocketSession.isOpen()) {
//                    currentWebSocketSession.close(status);
//                    sessionList.remove(currentWebSocketSession);
//                }
//
//            userSessions.put(sessionKey,sessionList);
//            if (userSessions.get(sessionKey) == null) {
//                userSessions.remove(sessionKey);
//                subscribedAppUsers.remove(sessionKey);
//            }
//        } catch (IOException e) {
//            logger.error("failed to closeConnection for: "+sessionKey+" ||"+e.getMessage());
//        }
//    }
//
//    public boolean sessionExists(String sessionKey) {
//        try {
//            if (userSessions.containsKey(sessionKey)) {
//                CopyOnWriteArrayList<WebSocketSession> sessionList = userSessions.get(sessionKey);
//                for (WebSocketSession session : sessionList) {
//                    if (session != null && !session.isOpen()) {
//                        sessionList.remove(session);
//                        closeConnection(sessionKey, session);
//                    }
//            }
//                userSessions.put(sessionKey,sessionList);
//                if (!userSessions.get(sessionKey).isEmpty())
//                    return true;
//        }
//        } catch (Exception e) {
//            logger.error("unable to find if the session exists, sessionKey = "+sessionKey);
//        }
//
//        return false;
//    }
//}
//
//
//@Data
//class UserPayload {
//    private Long user;
//
//    @Override
//    public String toString() {
//        return "UserPayload{" +
//                "user=" + user +
//                '}';
//    }
//}
