package com.uvend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UvendApplication {
    public static void main(String[] args) {
        SpringApplication.run(UvendApplication.class, args);
    }
}
