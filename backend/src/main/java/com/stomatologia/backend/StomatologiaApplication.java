package com.stomatologia.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class StomatologiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(StomatologiaApplication.class, args);
    }
}
