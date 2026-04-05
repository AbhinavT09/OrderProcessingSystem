package com.example.orderprocessing.infrastructure.messaging.schema;

/**
 * Abstraction for external schema registry integrations (Confluent, Glue, etc).
 */
public interface ExternalSchemaRegistryClient {

    int latestVersion(String subject);

    int normalizeIncomingVersion(String subject, Integer incomingVersion);

    void assertWriterCompatible(String subject, int writerVersion);

    void assertReaderCompatible(String subject, int payloadVersion);
}
