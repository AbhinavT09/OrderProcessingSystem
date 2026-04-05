package com.example.orderprocessing.infrastructure.messaging.retry;

import com.example.orderprocessing.infrastructure.resilience.BackpressureManager;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLTransientException;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveRetryPolicyStrategy implements RetryPolicyStrategy {

    private final BackpressureManager backpressureManager;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double jitterPercent;

    public AdaptiveRetryPolicyStrategy(
            BackpressureManager backpressureManager,
            @Value("${app.outbox.backoff-base-ms:1000}") long baseDelayMs,
            @Value("${app.outbox.backoff-max-ms:300000}") long maxDelayMs,
            @Value("${app.outbox.backoff-jitter-percent:0.2}") double jitterPercent) {
        this.backpressureManager = backpressureManager;
        this.baseDelayMs = Math.max(100, baseDelayMs);
        this.maxDelayMs = Math.max(this.baseDelayMs, maxDelayMs);
        this.jitterPercent = Math.max(0.0D, Math.min(0.50D, jitterPercent));
    }

    @Override
    public RetryPlan plan(Throwable throwable, int retryCount) {
        RetryClassification classification = classify(throwable);
        long delay = delayFor(classification, Math.max(1, retryCount));
        long withJitter = applyJitter(delay);
        String failureType = inferFailureType(throwable);
        String failureReason = sanitizeFailureReason(throwable);
        return new RetryPlan(classification, withJitter, failureType, failureReason);
    }

    private RetryClassification classify(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root instanceof IllegalArgumentException || root instanceof jakarta.validation.ValidationException) {
            return RetryClassification.PERMANENT;
        }
        if (root instanceof DataAccessException) {
            return RetryClassification.SEMI_TRANSIENT;
        }
        if (root instanceof ConnectException
                || root instanceof SocketException
                || root instanceof SocketTimeoutException
                || root instanceof java.util.concurrent.TimeoutException
                || root instanceof SQLTransientException) {
            return RetryClassification.TRANSIENT;
        }
        return RetryClassification.SEMI_TRANSIENT;
    }

    private long delayFor(RetryClassification classification, int retryCount) {
        long exponent = Math.max(0, retryCount - 1);
        double classFactor = switch (classification) {
            case TRANSIENT -> 1.0D;
            case SEMI_TRANSIENT -> 1.75D;
            case PERMANENT -> 4.0D;
        };
        long exponential = Math.min(maxDelayMs, (long) (baseDelayMs * Math.pow(2.0D, exponent)));
        long loadAdjusted = (long) (exponential * classFactor / Math.max(0.2D, backpressureManager.throttlingFactor()));
        return Math.min(maxDelayMs, Math.max(baseDelayMs, loadAdjusted));
    }

    private long applyJitter(long delayMs) {
        if (jitterPercent <= 0.0D) {
            return delayMs;
        }
        double delta = delayMs * jitterPercent;
        double jitter = ThreadLocalRandom.current().nextDouble(-delta, delta);
        long value = (long) (delayMs + jitter);
        return Math.max(baseDelayMs, Math.min(maxDelayMs, value));
    }

    private String inferFailureType(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root instanceof ConnectException || root instanceof SocketException) {
            return "NETWORK";
        }
        if (root instanceof SocketTimeoutException || root instanceof java.util.concurrent.TimeoutException) {
            return "TIMEOUT";
        }
        if (root instanceof IllegalArgumentException || root instanceof jakarta.validation.ValidationException) {
            return "VALIDATION";
        }
        return "SYSTEM";
    }

    private String sanitizeFailureReason(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        if (message.length() > 256) {
            return message.substring(0, 256);
        }
        return message;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? new RuntimeException("unknown") : current;
    }
}
