package com.chubb.claims.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic claimEventsTopic(@Value("${app.kafka.claim-events-topic}") String topic,
                              @Value("${app.kafka.partitions}") int partitions,
                              @Value("${app.kafka.replication-factor}") int replicationFactor) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicationFactor).build();
    }
}
