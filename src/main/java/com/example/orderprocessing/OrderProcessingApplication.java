package com.example.orderprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
@EnableScheduling
/**
 * Spring Boot entrypoint for the distributed order-processing service.
 *
 * <p><b>Runtime role:</b> boots application wiring, scheduled outbox workers, and retry
 * infrastructure through {@code @EnableScheduling} and {@code @EnableRetry}.</p>
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
