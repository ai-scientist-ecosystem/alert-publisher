package com.aiscientist.alert_publisher.service;

import com.aiscientist.alert_publisher.dto.AlertMessage;
import com.aiscientist.alert_publisher.model.PublishedAlert;
import com.aiscientist.alert_publisher.repository.PublishedAlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Main service for publishing alerts via multiple channels
 */
@Service
@Slf4j
public class AlertPublisherService {

    private final Optional<PublishedAlertRepository> publishedAlertRepository;
    private final CellBroadcastService cellBroadcastService;
    private final FirebaseMessagingService firebaseMessagingService;
    private final KafkaTemplate<String, AlertMessage> kafkaTemplate;

    public AlertPublisherService(
            @Autowired(required = false) PublishedAlertRepository publishedAlertRepository,
            CellBroadcastService cellBroadcastService,
            FirebaseMessagingService firebaseMessagingService,
            KafkaTemplate<String, AlertMessage> kafkaTemplate) {
        this.publishedAlertRepository = Optional.ofNullable(publishedAlertRepository);
        this.cellBroadcastService = cellBroadcastService;
        this.firebaseMessagingService = firebaseMessagingService;
        this.kafkaTemplate = kafkaTemplate;
        
        if (this.publishedAlertRepository.isEmpty()) {
            log.warn("PublishedAlertRepository not available - database persistence disabled");
        }
    }

    /**
     * Publish alert through all configured channels
     */
    @Transactional
    public void publishAlert(AlertMessage alertMessage) {
        log.info("Publishing alert: {} - Severity: {} - Type: {}", 
                alertMessage.getId(), alertMessage.getSeverity(), alertMessage.getAlertType());

        String alertId = alertMessage.getId() != null ? alertMessage.getId().toString() : "UNKNOWN";
        
        // Check if already published (only if database available)
        if (publishedAlertRepository.isPresent()) {
            if (publishedAlertRepository.get().findByAlertId(alertId).isPresent()) {
                log.warn("Alert already published: {}", alertId);
                return;
            }
        }

        // Create PublishedAlert entity (only if database available)
        PublishedAlert publishedAlert = null;
        if (publishedAlertRepository.isPresent()) {
            String message = buildAlertMessage(alertMessage);
            publishedAlert = PublishedAlert.builder()
                    .alertId(alertId)
                    .severity(alertMessage.getSeverity())
                    .alertType(alertMessage.getAlertType())
                    .message(message)
                    .details(alertMessage.getDescription())
                    .detectedAt(alertMessage.getTimestamp() != null ? alertMessage.getTimestamp() : Instant.now())
                    .publishedAt(Instant.now())
                    .cellBroadcastStatus(PublishedAlert.PublishStatus.IN_PROGRESS)
                    .fcmStatus(PublishedAlert.PublishStatus.IN_PROGRESS)
                    .build();

            publishedAlert = publishedAlertRepository.get().save(publishedAlert);
        }

        // Publish via Cell Broadcast (async)
        final PublishedAlert finalAlert = publishedAlert;
        cellBroadcastService.broadcast(alertMessage)
                .subscribe(result -> handleCellBroadcastResult(finalAlert, result));

        // Publish via FCM (async)
        firebaseMessagingService.sendNotification(alertMessage)
                .thenAccept(result -> handleFcmResult(finalAlert, result));

        log.info("Alert publishing initiated: {} - Type: {}", alertId, alertMessage.getAlertType());
    }

    /**
     * Build human-readable alert message
     */
    private String buildAlertMessage(AlertMessage alert) {
        if (alert.getDescription() != null) {
            return alert.getDescription();
        }
        
        // Build message based on alert type
        if ("EARTHQUAKE".equals(alert.getAlertType())) {
            return String.format("Earthquake M%.1f detected at %s", 
                    alert.getMagnitude(), alert.getLocation());
        } else if ("SPACE_WEATHER".equals(alert.getAlertType())) {
            return String.format("Geomagnetic storm warning - Kp index: %.1f", alert.getKpValue());
        } else if ("FLOOD".equals(alert.getAlertType())) {
            return String.format("Flood warning at %s - Water level: %.1f ft", 
                    alert.getStationName(), alert.getWaterLevelFeet());
        }
        
        return "Disaster alert - Check details for more information";
    }

    /**
     * Handle Cell Broadcast result
     */
    private void handleCellBroadcastResult(PublishedAlert alert, 
                                          CellBroadcastService.CellBroadcastResult result) {
        if (alert != null && publishedAlertRepository.isPresent()) {
            alert.setCellBroadcastStatus(result.isSuccess() 
                    ? PublishedAlert.PublishStatus.SUCCESS 
                    : PublishedAlert.PublishStatus.FAILED);
            alert.setCellBroadcastMessageId(result.getMessageId());
            alert.setRecipientCount(alert.getRecipientCount() + result.getRecipientCount());
            
            if (result.isSuccess()) {
                alert.setSuccessCount(alert.getSuccessCount() + result.getRecipientCount());
            } else {
                alert.setFailureCount(alert.getFailureCount() + 1);
                alert.setErrorMessage(result.getMessage());
            }

            publishedAlertRepository.get().save(alert);

            // Publish result to Kafka
            publishResultToKafka(alert, result.isSuccess());
        } else {
            log.info("Cell Broadcast result: {} - {}", result.isSuccess() ? "SUCCESS" : "FAILED", result.getMessage());
        }
    }

    /**
     * Handle FCM result
     */
    private void handleFcmResult(PublishedAlert alert, 
                                 FirebaseMessagingService.FcmResult result) {
        if (alert != null && publishedAlertRepository.isPresent()) {
            alert.setFcmStatus(result.isSuccess() 
                    ? PublishedAlert.PublishStatus.SUCCESS 
                    : PublishedAlert.PublishStatus.FAILED);
            alert.setFcmMessageId(result.getMessageId());
            alert.setRecipientCount(alert.getRecipientCount() + result.getRecipientCount());
            alert.setSuccessCount(alert.getSuccessCount() + result.getSuccessCount());
            alert.setFailureCount(alert.getFailureCount() + result.getFailureCount());

            if (!result.isSuccess()) {
                String errorMsg = alert.getErrorMessage() != null 
                        ? alert.getErrorMessage() + "; " + result.getMessage()
                        : result.getMessage();
                alert.setErrorMessage(errorMsg);
            }

            publishedAlertRepository.get().save(alert);

            // Publish result to Kafka
            publishResultToKafka(alert, result.isSuccess());
        } else {
            log.info("FCM result: {} - Success: {}, Failed: {}", 
                    result.isSuccess() ? "SUCCESS" : "FAILED", 
                    result.getSuccessCount(), result.getFailureCount());
        }
    }

    /**
     * Publish publishing result to Kafka
     */
    private void publishResultToKafka(PublishedAlert alert, boolean success) {
        String topic = success ? "alerts.published" : "alerts.failed";
        
        AlertMessage resultMessage = AlertMessage.builder()
                .id(java.util.UUID.fromString(alert.getAlertId()))
                .severity(alert.getSeverity())
                .alertType(alert.getAlertType())
                .description(alert.getMessage())
                .timestamp(alert.getDetectedAt())
                .build();

        kafkaTemplate.send(topic, alert.getAlertId(), resultMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish result to Kafka: {}", ex.getMessage());
                    } else {
                        log.info("Published result to Kafka topic: {}", topic);
                    }
                });
    }

    /**
     * Retry failed alerts
     */
    @Transactional
    public void retryFailedAlerts(int maxRetries) {
        if (publishedAlertRepository.isEmpty()) {
            log.warn("Cannot retry alerts - database not available");
            return;
        }
        
        var failedAlerts = publishedAlertRepository.get().findAlertsForRetry(maxRetries);
        
        log.info("Retrying {} failed alerts", failedAlerts.size());

        for (PublishedAlert alert : failedAlerts) {
            alert.setRetryCount(alert.getRetryCount() + 1);
            alert.setLastRetryAt(Instant.from(LocalDateTime.now()));
            publishedAlertRepository.get().save(alert);

            AlertMessage alertMessage = AlertMessage.builder()
                    .id(java.util.UUID.fromString(alert.getAlertId()))
                    .severity(alert.getSeverity())
                    .alertType(alert.getAlertType())
                    .description(alert.getMessage())
                    .timestamp(alert.getDetectedAt())
                    .build();

            // Retry Cell Broadcast if failed
            if (alert.getCellBroadcastStatus() == PublishedAlert.PublishStatus.FAILED) {
                cellBroadcastService.broadcast(alertMessage)
                        .subscribe(result -> handleCellBroadcastResult(alert, result));
            }

            // Retry FCM if failed
            if (alert.getFcmStatus() == PublishedAlert.PublishStatus.FAILED) {
                firebaseMessagingService.sendNotification(alertMessage)
                        .thenAccept(result -> handleFcmResult(alert, result));
            }
        }
    }
}
