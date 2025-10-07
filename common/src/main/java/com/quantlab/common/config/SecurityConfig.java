package com.quantlab.common.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF if not needed
                .cors(withDefaults()) // Disable CORS completely
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // Allow all requests


        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173/", "http://localhost:5173",
                "http://localhost:5174/", "http://localhost:5174",
                "https://torus.quantlabdemo.com/", "https://torus.quantlabdemo.com",
                "https://deltaapi.assuranceprepservices.com/", "https://deltaapi.assuranceprepservices.com",
                "https://cug-app.torusdigital.com/", "https://cug-app.torusdigital.com","https://www.torusdigital.com"
        ));
        configuration.addAllowedOriginPattern("https://*.quantlabdemo.com");
        configuration.addAllowedOriginPattern("https://*.assuranceprepservices.com");
        configuration.addAllowedOriginPattern("https://*.torusdigital.com");
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        source.registerCorsConfiguration("/ql/**", configuration);
        return source;
    }
}