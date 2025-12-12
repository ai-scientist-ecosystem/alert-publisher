package com.aiscientist.alert_publisher.service;

import com.aiscientist.alert_publisher.dto.AlertMessage;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for Firebase Cloud Messaging (FCM) push notifications
 * This implementation can send to individual devices or topics
 */
@Service
@Slf4j
public class FirebaseMessagingService {

    private final boolean enabled;
    private final int timeoutSeconds;
    private final int retryAttempts;

    public FirebaseMessagingService(
            @Value("${alert.publisher.fcm.enabled}") boolean enabled,
            @Value("${alert.publisher.fcm.timeout-seconds}") int timeoutSeconds,
            @Value("${alert.publisher.fcm.retry-attempts}") int retryAttempts) {
        
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
        this.retryAttempts = retryAttempts;
    }

    /**
     * Send push notification via FCM to all subscribed devices
     */
    @Async
    public CompletableFuture<FcmResult> sendNotification(AlertMessage alert) {
        if (!enabled) {
            log.info("FCM is disabled. Skipping alert: {}", alert.getAlertId());
            return CompletableFuture.completedFuture(FcmResult.builder()
                    .success(true)
                    .messageId("SKIPPED-" + alert.getAlertId())
                    .recipientCount(0)
                    .message("FCM disabled")
                    .build());
        }

        log.info("Sending FCM notification for alert: {}", alert.getAlertId());

        // Mock implementation for MVP - actual FCM will be implemented when Firebase is configured
        return simulateFcmNotification(alert);
    }

    /**
     * Simulate FCM notification for MVP
     * In production, this will use actual Firebase Admin SDK
     */
    private CompletableFuture<FcmResult> simulateFcmNotification(AlertMessage alert) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate network delay
                Thread.sleep(500);
                
                // Simulate 98% success rate
                boolean success = Math.random() < 0.98;
                
                if (success) {
                    String messageId = "FCM-" + System.currentTimeMillis();
                    int recipientCount = calculateFcmRecipients(alert);
                    
                    log.info("FCM SUCCESS - MessageID: {}, Recipients: {}, AlertID: {}",
                            messageId, recipientCount, alert.getAlertId());
                    
                    return FcmResult.builder()
                            .success(true)
                            .messageId(messageId)
                            .recipientCount(recipientCount)
                            .successCount(recipientCount)
                            .failureCount(0)
                            .message("FCM notification sent successfully")
                            .build();
                } else {
                    log.error("FCM FAILED - AlertID: {}", alert.getAlertId());
                    return FcmResult.builder()
                            .success(false)
                            .messageId(null)
                            .recipientCount(0)
                            .successCount(0)
                            .failureCount(1)
                            .message("FCM error: Invalid registration token")
                            .build();
                }
            } catch (Exception e) {
                log.error("FCM error: {}", e.getMessage());
                return FcmResult.builder()
                        .success(false)
                        .messageId(null)
                        .recipientCount(0)
                        .successCount(0)
                        .failureCount(1)
                        .message("Error: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Build FCM message from alert
     */
    private Message buildFcmMessage(AlertMessage alert, String topic) {
        return Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder()
                        .setTitle(buildNotificationTitle(alert))
                        .setBody(alert.getMessage())
                        .build())
                .putData("alertId", alert.getAlertId())
                .putData("severity", alert.getSeverity())
                .putData("alertType", alert.getAlertType())
                .putData("detectedAt", alert.getDetectedAt().toString())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(getPriority(alert.getSeverity()))
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .setChannelId(getChannelId(alert.getSeverity()))
                                .setPriority(AndroidNotification.Priority.HIGH)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .setBadge(1)
                                .build())
                        .build())
                .build();
    }

    private String buildNotificationTitle(AlertMessage alert) {
        return String.format("[%s] %s Alert", alert.getSeverity(), alert.getAlertType());
    }

    private AndroidConfig.Priority getPriority(String severity) {
        return "CRITICAL".equals(severity) || "HIGH".equals(severity) 
                ? AndroidConfig.Priority.HIGH 
                : AndroidConfig.Priority.NORMAL;
    }

    private String getChannelId(String severity) {
        return "alerts_" + severity.toLowerCase();
    }

    /**
     * Calculate estimated FCM recipients
     * In production, this would query actual subscriber count from Firebase
     */
    private int calculateFcmRecipients(AlertMessage alert) {
        return switch (alert.getSeverity()) {
            case "CRITICAL" -> 500000;  // 500K subscribers
            case "HIGH" -> 200000;      // 200K subscribers
            case "MEDIUM" -> 50000;     // 50K subscribers
            default -> 10000;           // 10K subscribers
        };
    }

    /**
     * Result of FCM operation
     */
    @lombok.Data
    @lombok.Builder
    public static class FcmResult {
        private boolean success;
        private String messageId;
        private int recipientCount;
        private int successCount;
        private int failureCount;
        private String message;
    }
}
