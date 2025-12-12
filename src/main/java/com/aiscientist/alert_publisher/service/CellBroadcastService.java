package com.aiscientist.alert_publisher.service;

import com.aiscientist.alert_publisher.dto.AlertMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Service for Cell Broadcast integration with Telecom APIs
 * This is a mock implementation for MVP - production will integrate with actual telecom providers
 */
@Service
@Slf4j
public class CellBroadcastService {

    private final WebClient webClient;
    private final boolean enabled;
    private final String apiUrl;
    private final String apiKey;
    private final int timeoutSeconds;
    private final int retryAttempts;

    public CellBroadcastService(
            WebClient.Builder webClientBuilder,
            @Value("${alert.publisher.cell-broadcast.enabled}") boolean enabled,
            @Value("${alert.publisher.cell-broadcast.api-url}") String apiUrl,
            @Value("${alert.publisher.cell-broadcast.api-key}") String apiKey,
            @Value("${alert.publisher.cell-broadcast.timeout-seconds}") int timeoutSeconds,
            @Value("${alert.publisher.cell-broadcast.retry-attempts}") int retryAttempts) {
        
        this.webClient = webClientBuilder.baseUrl(apiUrl).build();
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.retryAttempts = retryAttempts;
    }

    /**
     * Broadcast alert via Cell Broadcast system
     */
    @Async
    public Mono<CellBroadcastResult> broadcast(AlertMessage alert) {
        if (!enabled) {
            log.info("Cell Broadcast is disabled. Skipping alert: {}", alert.getAlertId());
            return Mono.just(CellBroadcastResult.builder()
                    .success(true)
                    .messageId("SKIPPED-" + alert.getAlertId())
                    .recipientCount(0)
                    .message("Cell Broadcast disabled")
                    .build());
        }

        log.info("Broadcasting alert via Cell Broadcast: {}", alert.getAlertId());

        // Mock implementation - in production, this will call actual telecom API
        return simulateCellBroadcast(alert);
    }

    /**
     * Simulate Cell Broadcast for MVP
     * In production, this will be replaced with actual telecom API calls
     */
    private Mono<CellBroadcastResult> simulateCellBroadcast(AlertMessage alert) {
        return Mono.fromCallable(() -> {
            // Simulate network delay
            Thread.sleep(1000);
            
            // Simulate 95% success rate
            boolean success = Math.random() < 0.95;
            
            if (success) {
                String messageId = "CB-" + System.currentTimeMillis();
                int recipientCount = calculateRecipientCount(alert);
                
                log.info("Cell Broadcast SUCCESS - MessageID: {}, Recipients: {}, AlertID: {}",
                        messageId, recipientCount, alert.getAlertId());
                
                return CellBroadcastResult.builder()
                        .success(true)
                        .messageId(messageId)
                        .recipientCount(recipientCount)
                        .message("Cell Broadcast sent successfully")
                        .build();
            } else {
                log.error("Cell Broadcast FAILED - AlertID: {}", alert.getAlertId());
                return CellBroadcastResult.builder()
                        .success(false)
                        .messageId(null)
                        .recipientCount(0)
                        .message("Telecom API error: Connection timeout")
                        .build();
            }
        }).timeout(Duration.ofSeconds(timeoutSeconds))
          .retry(retryAttempts)
          .onErrorResume(e -> {
              log.error("Cell Broadcast error after retries: {}", e.getMessage());
              return Mono.just(CellBroadcastResult.builder()
                      .success(false)
                      .messageId(null)
                      .recipientCount(0)
                      .message("Error: " + e.getMessage())
                      .build());
          });
    }

    /**
     * Calculate estimated recipient count based on alert radius
     */
    private int calculateRecipientCount(AlertMessage alert) {
        // Mock calculation based on radius
        if (alert.getRadius() != null) {
            // Assume 1000 people per km²
            double area = Math.PI * Math.pow(alert.getRadius(), 2);
            return (int) (area * 1000);
        }
        
        // Default for alerts without radius (global)
        return getSeverityBasedRecipients(alert.getSeverity());
    }

    private int getSeverityBasedRecipients(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 10000000; // 10M people
            case "HIGH" -> 1000000;      // 1M people
            case "MEDIUM" -> 100000;     // 100K people
            default -> 10000;            // 10K people
        };
    }

    /**
     * Result of Cell Broadcast operation
     */
    @lombok.Data
    @lombok.Builder
    public static class CellBroadcastResult {
        private boolean success;
        private String messageId;
        private int recipientCount;
        private String message;
    }
}
