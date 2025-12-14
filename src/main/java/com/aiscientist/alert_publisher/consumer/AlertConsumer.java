package com.aiscientist.alert_publisher.consumer;

import com.aiscientist.alert_publisher.dto.AlertMessage;
import com.aiscientist.alert_publisher.service.AlertPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for incoming alerts
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AlertConsumer {

    private final AlertPublisherService alertPublisherService;

    @KafkaListener(
            topics = {"${kafka.topics.alerts-critical}", "${kafka.topics.alerts-warning}"},
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAlert(
            @Payload AlertMessage alertMessage,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        
        log.info("Consumed alert from Kafka - Topic: {}, AlertID: {}, Severity: {}, Type: {}, Partition: {}, Offset: {}",
                topic,
                alertMessage.getId(), 
                alertMessage.getSeverity(),
                alertMessage.getAlertType(),
                partition,
                offset);

        try {
            alertPublisherService.publishAlert(alertMessage);
            log.info("Successfully processed alert: {} - {}", alertMessage.getId(), alertMessage.getAlertType());
        } catch (Exception e) {
            log.error("Error processing alert {} ({}): {}", 
                    alertMessage.getId(), 
                    alertMessage.getAlertType(), 
                    e.getMessage(), e);
            // In production, implement dead letter queue (DLQ) handling
        }
    }
}
