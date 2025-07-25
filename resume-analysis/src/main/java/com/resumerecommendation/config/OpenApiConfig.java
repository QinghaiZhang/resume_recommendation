package com.resumerecommendation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI resumeAnalysisOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("简历分析服务 API")
                        .description("简历分析服务提供简历解析、技能评估和改进建议等功能")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@resumerecommendation.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}