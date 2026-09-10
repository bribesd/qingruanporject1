package com.enterprise.kb.config;

import com.enterprise.kb.security.RequireAdminInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequireAdminInterceptor requireAdminInterceptor;
    private final String[] corsOrigins;

    public WebConfig(RequireAdminInterceptor requireAdminInterceptor,
                     @Value("${app.cors.origins:http://localhost:5173,http://127.0.0.1:5173}") String corsOrigins) {
        this.requireAdminInterceptor = requireAdminInterceptor;
        this.corsOrigins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsOrigins)
                .allowedMethods("*")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireAdminInterceptor).addPathPatterns("/api/**");
    }
}
