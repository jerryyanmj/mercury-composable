package com.accenture.ordersystem.common;

import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.system.PostOffice;

import java.util.Map;

/**
 * Publishing to Kafka through {@code simple.kafka.notification}.
 *
 * The contract is easy to get wrong and fails quietly when you do: the topic and the business
 * correlation-id ride as HEADERS and the body IS the payload - there is no message-key parameter,
 * and a {@code {topic, key, body}} wrapper map publishes nothing at all.
 *
 * Publication is deliberately SYNCHRONOUS. A fire-and-forget send swallows the missing-topic
 * exception, so the caller logs success while the message is silently dropped - which voids the
 * outbox guarantee for a relay, and loses an acknowledgement outright for a stateless forwarder.
 * Centralizing it here means that mistake cannot be reintroduced one call site at a time.
 */
public final class Events {

    private static final String NOTIFICATION_ROUTE = "simple.kafka.notification";
    private static final long DEFAULT_TIMEOUT_MS = 10_000;

    private Events() {
    }

    /**
     * Publish {@code payload} to {@code topic}, correlated by {@code businessCorrelationId}.
     * Throws if the broker rejects it, so the caller can leave the work un-acknowledged and retry.
     */
    public static void publish(PostOffice po, String topic, String businessCorrelationId,
                               Map<String, Object> payload) throws Exception {
        po.request(new EventEnvelope().setTo(NOTIFICATION_ROUTE)
                .setHeader("topic", topic)
                .setHeader("cid", businessCorrelationId)
                .setBody(payload), DEFAULT_TIMEOUT_MS).get();
    }

    public static void publish(Map<String, String> headers, int instance, String topic,
                               String businessCorrelationId, Map<String, Object> payload) throws Exception {
        publish(new PostOffice(headers, instance), topic, businessCorrelationId, payload);
    }
}
