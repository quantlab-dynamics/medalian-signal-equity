//package com.quantlab.client.websockets;
//
//import com.fasterxml.jackson.databind.DeserializationFeature;
//import com.quantlab.common.dao.EndpointProperties;
//import com.quantlab.common.dto.ApiResponseDTO;
//import com.quantlab.common.dto.ClientDetailsDTO;
//import com.quantlab.common.entity.UserAuthConstants;
//import com.quantlab.common.repository.UserAuthConstantsRepository;
//import com.quantlab.signal.dto.CookieDTO;
//import com.quantlab.signal.service.AuthService;
//import jakarta.annotation.PostConstruct;
//import lombok.Data;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.*;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.*;
//
//import static com.quantlab.client.websockets.SocketDataProcessorService.subscribedAppUsers;
//import static com.quantlab.common.utils.staticstore.AppConstants.*;
//
//@Component
//public class OpenPositionsSocketHandler extends TextWebSocketHandler {
//
//    private static final Logger logger = LogManager.getLogger(OpenPositionsSocketHandler.class);
//    public static Map<String, CopyOnWriteArrayList<WebSocketSession>> userSessions = new HashMap<>();
//
//    @Value("${validation_url}")
//    private String validationUrl;
//
//    @Autowired
//    SocketDataProcessorService socketDataProcessorService;
//
//    ObjectMapper objectMapper = new ObjectMapper();
//
//    @Autowired
//    UserAuthConstantsRepository userAuthConstantsRepository;
//
//    private final ScheduledExecutorService indexDataScheduler = Executors.newScheduledThreadPool(1);
//
//    private final Map<String, Object> currentIndexData = new ConcurrentHashMap<>();
//
//    public OpenPositionsSocketHandler() {
//        logger.info("OpenPositionsSocketHandler created");
//    }
//
//    @PostConstruct
//    public void init() {
//        indexDataScheduler.scheduleAtFixedRate(this::updateAndBroadcastIndexData,
//                0, 1000, TimeUnit.MILLISECONDS);
//    }
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException, InterruptedException {
//        try {
//            logger.info("### inside socket handleTextMessage message = "+message);
//            String sessionKey=validateUser(message.getPayload());
//            if (sessionKey == null || sessionKey.isEmpty()){
//                session.sendMessage(new TextMessage("User not Found"));
//                session.close();
//                return;
//            }
//            logger.info("SessionID = "+session.getId());
//
//            if (userSessions.containsKey(sessionKey)) {
//                CopyOnWriteArrayList<WebSocketSession> sessionsLists = userSessions.get(sessionKey);
//                sessionsLists.add(session);
//                userSessions.put(sessionKey,sessionsLists);
//            }else {
//                CopyOnWriteArrayList<WebSocketSession> sessionsLists = new CopyOnWriteArrayList<>();
//                sessionsLists.add(session);
//                userSessions.put(sessionKey,  sessionsLists);
//            }
//            logger.info("### inside socket handleTextMessage sessionKey = "+sessionKey);
//
//            socketDataProcessorService.startProcessing(sessionKey);
////            redisProcessingMultiThreadingService.startThreadProcessing(sessionKey);
//        } catch (Exception e) {
//            logger.error("failed at afterConnectionEstablished "+e.getMessage());
//            session.sendMessage(new TextMessage("unable to establish connection for user"));
//            if (session.isOpen())
//                session.close();
//        }
//    }
//
//    private String validateUser(String response){
//
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            CookieDTO cookieDTO = objectMapper.readValue(response, CookieDTO.class);
//
//            // REMOVE NEGATION (!) TO BY-PASS AUTHENTICATION
//            if (!SHOULD_AUTHENTICATE)
//                return cookieDTO.getUserId();
//
//            String token = cookieDTO.getToken();
//            HttpHeaders headers = new HttpHeaders();
//            headers.set("Authorization", "Bearer " + token);
//
//            HttpEntity<String> entity = new HttpEntity<>(headers);
//
//            RestTemplate restTemplate = new RestTemplate();
//            ResponseEntity<String> apiResponse = restTemplate.exchange(validationUrl, HttpMethod.GET, entity, String.class);
//
//            if (apiResponse.getStatusCode() != HttpStatus.OK) {
//                throw new Exception("Invalid apiResponse from validation endpoint: " + apiResponse.getStatusCode());
//            }
//            ObjectMapper responseObjectMapper = new ObjectMapper();
//            responseObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//            ApiResponseDTO apiResponseDTO = responseObjectMapper.readValue(apiResponse.getBody(), ApiResponseDTO.class);
//
//            if (apiResponseDTO == null) {
//                throw new Exception("Invalid token or user");
//            }
//            ClientDetailsDTO clientDetailsDTO = apiResponseDTO.processResponse();
//
//            // Extract clientId from the apiResponse
//            Optional<UserAuthConstants> userAuthConstants = userAuthConstantsRepository.findByClientId(clientDetailsDTO.getClientId());
//            return userAuthConstants.map(authConstants -> authConstants.getAppUser().getAppUserId().toString()).orElse(null);
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
//    public void updateAndBroadcastIndexData() {
//        List<Map<String, Object>> indexData = socketDataProcessorService.fetchLiveIndexData();
//
//        Map<String, Object> wrapper = new HashMap<>();
//        wrapper.put("type", "INDEX_DATA");
//        wrapper.put("data", indexData);
//
//        String json;
//        try {
//            json = objectMapper.writeValueAsString(wrapper);
//            TextMessage message = new TextMessage(json);
//
//            for (Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry : userSessions.entrySet()) {
//                for (WebSocketSession session : entry.getValue()) {
//                    if (session.isOpen()) {
//                        try {
//                            session.sendMessage(message);
//                        } catch (Exception e) {
//                            logger.error("Error sending to session {}: {}", session.getId(), e.getMessage());
//                            session.close();
//                        }
//                    }
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
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
//                    Map<String, Object> wrapper = new HashMap<>();
//                    wrapper.put("type", "OPEN_POSITIONS");
//                    wrapper.put("data", marketData);
//                    String data = objectMapper.writeValueAsString(wrapper);
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
//            CloseStatus status = new CloseStatus(4000, "socket disconnected");
//            CopyOnWriteArrayList<WebSocketSession> sessionList = userSessions.get(sessionKey);
//            if (currentWebSocketSession != null && !currentWebSocketSession.isOpen()) {
//                currentWebSocketSession.close(status);
//                sessionList.remove(currentWebSocketSession);
//            }
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
//                }
//                userSessions.put(sessionKey,sessionList);
//                if (!userSessions.get(sessionKey).isEmpty())
//                    return true;
//            }
//        } catch (Exception e) {
//            logger.error("unable to find if the session exists, sessionKey = "+sessionKey);
//        }
//
//        return false;
//    }
//}
