package com.iliaspiotopoulos.bibliocore;

import com.iliaspiotopoulos.bibliocore.config.JwtProperties;
import com.iliaspiotopoulos.bibliocore.config.LoanProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({LoanProperties.class, JwtProperties.class})
public class BiblioCoreApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiblioCoreApiApplication.class, args);
    }
}
