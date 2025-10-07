package com.quantlab.common.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Swagger UI resources

        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:5173/",
                        "http://localhost:5174/",
                        "https://torus.quantlabdemo.com",
                        "https://deltaapi.assuranceprepservices.com",
                        "https://cug-app.torusdigital.com",
                        "https://www.torusdigital.com"
                )
                .allowedOriginPatterns(
                        "https://*.quantlabdemo.com",
                        "https://*.assuranceprepservices.com",
                        "https://*.torusdigital.com"  // To allow all subdomains of torusdigital.com
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}