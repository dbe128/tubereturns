package com.tubereturns.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final BuildProperties buildProperties;

    @GetMapping
    public Map<String, String> version() {
        return Map.of("version", buildProperties.getVersion());
    }
}
