package com.example.orderprocessing.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.event.ConsumerPartitionPausedEvent;
import org.springframework.kafka.event.ConsumerPausedEvent;
import org.springframework.kafka.event.ConsumerPartitionResumedEvent;
import org.springframework.kafka.event.ConsumerResumedEvent;
import org.springframework.kafka.event.ConsumerStartedEvent;
import org.springframework.kafka.event.ConsumerStoppedEvent;
import org.springframework.stereotype.Component;

@Component
/**
 * KafkaConsumerRebalanceObserver implements a concrete responsibility in the order processing service.
 * It is used to keep the boots the Spring runtime for the service layer explicit and maintainable in this architecture.
 */
public class KafkaConsumerRebalanceObserver {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerRebalanceObserver.class);

    @EventListener
    /**
     * Executes onConsumerStarted.
     * @param event input argument used by this operation
     */
    public void onConsumerStarted(ConsumerStartedEvent event) {
        log.info("Kafka consumer started source={}", event.getSource());
    }

    @EventListener
    /**
     * Executes onConsumerStopped.
     * @param event input argument used by this operation
     */
    public void onConsumerStopped(ConsumerStoppedEvent event) {
        log.warn("Kafka consumer stopped source={} reason={}", event.getSource(), event.getReason());
    }

    @EventListener
    /**
     * Executes onConsumerPaused.
     * @param event input argument used by this operation
     */
    public void onConsumerPaused(ConsumerPausedEvent event) {
        log.warn("Kafka consumer partitions paused partitions={}", event.getPartitions());
    }

    @EventListener
    /**
     * Executes onConsumerResumed.
     * @param event input argument used by this operation
     */
    public void onConsumerResumed(ConsumerResumedEvent event) {
        log.info("Kafka consumer partitions resumed partitions={}", event.getPartitions());
    }

    @EventListener
    /**
     * Executes onPartitionPaused.
     * @param event input argument used by this operation
     */
    public void onPartitionPaused(ConsumerPartitionPausedEvent event) {
        log.warn("Kafka consumer single partition paused partition={}", event.getPartitions());
    }

    @EventListener
    /**
     * Executes onPartitionResumed.
     * @param event input argument used by this operation
     */
    public void onPartitionResumed(ConsumerPartitionResumedEvent event) {
        log.info("Kafka consumer single partition resumed partition={}", event.getPartition());
    }
}
