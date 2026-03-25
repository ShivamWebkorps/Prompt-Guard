package com.promptguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PromptGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromptGuardApplication.class, args);
    }
}
