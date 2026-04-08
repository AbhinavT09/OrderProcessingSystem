package com.example.orderprocessing.integration;

import com.example.orderprocessing.OrderProcessingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the application wires against a real Kafka broker from Testcontainers (no embedded
 * broker property ordering issues vs {@code application.yml} defaults).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = OrderProcessingApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.task.scheduling.enabled=false",
        "app.security.jwt-secret=test-secret-for-integration-tests-123456",
        "spring.data.redis.sentinel.master=test-master",
        "spring.data.redis.sentinel.nodes=localhost:26379"
})
class KafkaContainerApplicationIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void kafkaTemplateIsWired() {
        assertNotNull(kafkaTemplate);
    }
}
