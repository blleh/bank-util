package com.bankmapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Bank Transfer List Generator web interface.
 * Provides a web UI for HR users to upload CSV files and generate bank transfer lists.
 */
@SpringBootApplication
public class BankMapperApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankMapperApplication.class, args);
    }
} 