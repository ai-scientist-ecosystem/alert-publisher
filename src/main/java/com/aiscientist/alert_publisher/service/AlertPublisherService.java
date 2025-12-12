package com.aiscientist.alert_publisher.service;

import com.aiscientist.alert_publisher.dto.AlertMessage;
import com.aiscientist.alert_publisher.model.PublishedAlert;
import com.aiscientist.alert_publisher.repository.PublishedAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Main service for publishing alerts via multiple channels
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertPublisherService {

    private final PublishedAlertRepository publishedAlertRepository;
    private final CellBroadcastService cellBroadcastService;
    private final FirebaseMessagingService firebaseMessagingService;
    private final KafkaTemplate<String, AlertMessage> kafkaTemplate;

    /**
     * Publish alert through all configured channels
     */
    @Transactional
    public void publishAlert(AlertMessage alertMessage) {
        log.info("Publishing alert: {} - Severity: {}", 
                alertMessage.getAlertId(), alertMessage.getSeverity());

        // Check if already published
        if (publishedAlertRepository.findByAlertId(alertMessage.getAlertId()).isPresent()) {
            log.warn("Alert already published: {}", alertMessage.getAlertId());
            return;
        }

        // Create PublishedAlert entity
        PublishedAlert publishedAlert = PublishedAlert.builder()
                .alertId(alertMessage.getAlertId())
                .severity(alertMessage.getSeverity())
                .alertType(alertMessage.getAlertType())
                .message(alertMessage.getMessage())
                .details(alertMessage.getDetails())
                .detectedAt(alertMessage.getDetectedAt())
                .publishedAt(LocalDateTime.now())
                .cellBroadcastStatus(PublishedAlert.PublishStatus.IN_PROGRESS)
                .fcmStatus(PublishedAlert.PublishStatus.IN_PROGRESS)
                .build();

        publishedAlert = publishedAlertRepository.save(publishedAlert);

        // Publish via Cell Broadcast (async)
        final PublishedAlert finalAlert = publishedAlert;
        cellBroadcastService.broadcast(alertMessage)
                .subscribe(result -> handleCellBroadcastResult(finalAlert, result));

        // Publish via FCM (async)
        firebaseMessagingService.sendNotification(alertMessage)
                .thenAccept(result -> handleFcmResult(finalAlert, result));

        log.info("Alert publishing initiated: {}", alertMessage.getAlertId());
    }

    /**
     * Handle Cell Broadcast result
     */
    private void handleCellBroadcastResult(PublishedAlert alert, 
                                          CellBroadcastService.CellBroadcastResult result) {
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

        publishedAlertRepository.save(alert);

        // Publish result to Kafka
        publishResultToKafka(alert, result.isSuccess());
    }

    /**
     * Handle FCM result
     */
    private void handleFcmResult(PublishedAlert alert, 
                                 FirebaseMessagingService.FcmResult result) {
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

        publishedAlertRepository.save(alert);

        // Publish result to Kafka
        publishResultToKafka(alert, result.isSuccess());
    }

    /**
     * Publish publishing result to Kafka
     */
    private void publishResultToKafka(PublishedAlert alert, boolean success) {
        String topic = success ? "alerts.published" : "alerts.failed";
        
        AlertMessage resultMessage = AlertMessage.builder()
                .alertId(alert.getAlertId())
                .severity(alert.getSeverity())
                .alertType(alert.getAlertType())
                .message(alert.getMessage())
                .detectedAt(alert.getDetectedAt())
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
        var failedAlerts = publishedAlertRepository.findAlertsForRetry(maxRetries);
        
        log.info("Retrying {} failed alerts", failedAlerts.size());

        for (PublishedAlert alert : failedAlerts) {
            alert.setRetryCount(alert.getRetryCount() + 1);
            alert.setLastRetryAt(LocalDateTime.now());
            publishedAlertRepository.save(alert);

            AlertMessage alertMessage = AlertMessage.builder()
                    .alertId(alert.getAlertId())
                    .severity(alert.getSeverity())
                    .alertType(alert.getAlertType())
                    .message(alert.getMessage())
                    .details(alert.getDetails())
                    .detectedAt(alert.getDetectedAt())
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
