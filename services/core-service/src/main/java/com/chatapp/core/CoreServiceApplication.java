package com.chatapp.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.chatapp.core.base.config.GeoIpProperties;
import com.chatapp.core.base.config.JwtProperties;
import com.chatapp.core.base.config.OAuthProperties;
import com.chatapp.core.base.config.OtpProperties;
import com.chatapp.core.base.config.OutboxProperties;
import com.chatapp.core.base.config.TotpProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, OtpProperties.class, TotpProperties.class, GeoIpProperties.class, OAuthProperties.class, OutboxProperties.class})
@EnableScheduling
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
