package com.tubereturns.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tubeReturnsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TubeReturns API")
                        .description("API for ranking finance YouTubers based on their historical investing performance")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("TubeReturns Team")
                                .email("contact@tubereturns.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}