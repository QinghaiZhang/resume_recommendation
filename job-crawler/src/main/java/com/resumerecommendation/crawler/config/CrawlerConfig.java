package com.resumerecommendation.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "crawler")
public class CrawlerConfig {
    private List<JobSource> sources;
    private Integer threadCount;
    private Integer retryCount;
    private Long retryDelay;
    private Map<String, String> headers;

    @Data
    public static class JobSource {
        private String name;
        private String baseUrl;
        private String listUrl;
        private String detailUrlPattern;
        private Map<String, String> selectors;
        private Boolean useSelenium;
        private Integer pageSize;
        private String cityList;
    }
} 