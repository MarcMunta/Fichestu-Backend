package com.example.fichestu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FichestuApplication {

    public static void main(String[] args) {
        SpringApplication.run(FichestuApplication.class, args);
    }
}
