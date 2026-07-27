package com.dell.twin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot REST backend.
 * The @SpringBootApplication annotation enables component scanning,
 * auto‑configuration, and serves as the main configuration class.
 */

@SpringBootApplication
public class TwinBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(TwinBackendApplication.class, args);
    }
}