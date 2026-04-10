package com.autoops.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeConfig {
    private String apiKey;
    private String baseUrl;
    private String model;
}