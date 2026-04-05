package com.example.orderprocessing.infrastructure.messaging.schema;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default schema-registry client backed by runtime properties.
 *
 * <p>This keeps a hexagonal seam for swapping in Confluent/Glue clients while
 * preserving local behavior for tests and dev deployments.</p>
 */
@Component
public class PropertyBackedSchemaRegistryClient implements ExternalSchemaRegistryClient {

    private final int latestOrderCreatedVersion;
    private final int minReadableOrderCreatedVersion;

    public PropertyBackedSchemaRegistryClient(
            @Value("${app.kafka.schema-registry.order-created.latest-version:2}") int latestOrderCreatedVersion,
            @Value("${app.kafka.schema-registry.order-created.min-readable-version:1}") int minReadableOrderCreatedVersion) {
        this.latestOrderCreatedVersion = Math.max(1, latestOrderCreatedVersion);
        this.minReadableOrderCreatedVersion = Math.max(1, minReadableOrderCreatedVersion);
    }

    @Override
    public int latestVersion(String subject) {
        return latestOrderCreatedVersion;
    }

    @Override
    public int normalizeIncomingVersion(String subject, Integer incomingVersion) {
        if (incomingVersion == null) {
            return minReadableOrderCreatedVersion;
        }
        if (incomingVersion < minReadableOrderCreatedVersion) {
            throw new EventSchemaValidationException("Unsupported legacy schema version for " + subject + ": " + incomingVersion);
        }
        return Math.min(incomingVersion, latestOrderCreatedVersion);
    }

    @Override
    public void assertWriterCompatible(String subject, int writerVersion) {
        if (writerVersion > latestOrderCreatedVersion) {
            throw new EventSchemaValidationException("Writer version is ahead of registry for " + subject + ": " + writerVersion);
        }
    }

    @Override
    public void assertReaderCompatible(String subject, int payloadVersion) {
        if (payloadVersion < minReadableOrderCreatedVersion || payloadVersion > latestOrderCreatedVersion) {
            throw new EventSchemaValidationException("Reader cannot process schema version " + payloadVersion + " for " + subject);
        }
    }
}
