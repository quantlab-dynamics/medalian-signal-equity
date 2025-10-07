//package com.quantlab.client.websockets;
//
//import com.quantlab.client.filter.ValidationFilter;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.socket.config.annotation.EnableWebSocket;
//import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
//import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
//@Configuration
//@EnableWebSocket
//public class WebSocketConfig implements WebSocketConfigurer {
//
//
//    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
//    @Autowired
//    private OpenPositionsSocketHandler openPositionsSocketHandler;
//
//
//    @Override
//    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//        logger.info("Adding WebSocket handler to registry");
//        registry.addHandler(openPositionsSocketHandler, "/ws/open-positions-data")
//                .setAllowedOrigins(
//                        "http://localhost:5173/", "http://localhost:5173",
//                        "http://localhost:5174/", "http://localhost:5174",
//                        "https://torus.quantlabdemo.com", "https://torus.quantlabdemo.com/",
//                        "https://deltaapi.assuranceprepservices.com", "https://deltaapi.assuranceprepservices.com/",
//                        "https://cug-app.torusdigital.com", "https://cug-app.torusdigital.com/",
//                        "https://app.torusdigital.com"
//                );
//    }
//
//}
