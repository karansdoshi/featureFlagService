package com.digitalocean.featureflags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FeatureFlagServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeatureFlagServiceApplication.class, args);
    }
}
