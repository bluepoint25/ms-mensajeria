package com.tiendanative.mensajeria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsMensajeriaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsMensajeriaApplication.class, args);
    }
}
