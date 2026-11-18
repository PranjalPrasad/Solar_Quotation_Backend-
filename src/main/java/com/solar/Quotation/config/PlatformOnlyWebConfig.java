package com.solar.Quotation.config;

import com.solar.Quotation.security.PlatformOnlyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PlatformOnlyWebConfig implements WebMvcConfigurer {

    private final PlatformOnlyInterceptor platformOnly;

    public PlatformOnlyWebConfig(PlatformOnlyInterceptor platformOnly) {
        this.platformOnly = platformOnly;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(platformOnly)
                .addPathPatterns("/api/channel-partners/**", "/api/partners/**", "/api/admin/**");
    }
}