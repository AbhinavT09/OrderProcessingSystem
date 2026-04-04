package com.example.orderprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
@EnableScheduling
/**
 * OrderProcessingApplication implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class OrderProcessingApplication {

    /**
     * Application entry point.
     * @param args input argument used by this operation
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderProcessingApplication.class, args);
    }
}
