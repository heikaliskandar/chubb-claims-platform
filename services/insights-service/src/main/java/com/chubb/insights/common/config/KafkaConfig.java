package com.chubb.insights.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConfig {

    private static final String DEAD_LETTER_SUFFIX = ".dlt";
    private static final long RETRY_INTERVAL_MS = 1000;
    private static final long MAX_RETRIES = 3;

    @Bean
    NewTopic claimEventsDeadLetterTopic(@Value("${app.kafka.claim-events-topic}") String topic,
                                        @Value("${app.kafka.partitions}") int partitions,
                                        @Value("${app.kafka.replication-factor}") int replicationFactor) {
        return TopicBuilder.name(topic + DEAD_LETTER_SUFFIX).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(deadLetters, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
        errorHandler.addNotRetryableExceptions(JacksonException.class);
        return errorHandler;
    }
}
