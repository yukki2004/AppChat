package com.chatapp.core.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "env", activeProfile);
    }
}
