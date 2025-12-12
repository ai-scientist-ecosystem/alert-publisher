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
            topics = "${kafka.topics.alerts-input}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAlert(
            @Payload AlertMessage alertMessage,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        log.info("Consumed alert from Kafka - AlertID: {}, Severity: {}, Partition: {}, Offset: {}",
                alertMessage.getAlertId(), 
                alertMessage.getSeverity(),
                partition,
                offset);

        try {
            alertPublisherService.publishAlert(alertMessage);
            log.info("Successfully processed alert: {}", alertMessage.getAlertId());
        } catch (Exception e) {
            log.error("Error processing alert {}: {}", alertMessage.getAlertId(), e.getMessage(), e);
            // In production, implement dead letter queue (DLQ) handling
        }
    }
}
