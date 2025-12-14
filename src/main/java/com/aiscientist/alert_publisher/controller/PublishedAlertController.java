package com.aiscientist.alert_publisher.controller;

import com.aiscientist.alert_publisher.model.PublishedAlert;
import com.aiscientist.alert_publisher.repository.PublishedAlertRepository;
import com.aiscientist.alert_publisher.service.AlertPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST API for alert publisher operations
 * Only available when database is configured
 */
@RestController
@RequestMapping("/api/v1/published-alerts")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(PublishedAlertRepository.class)
public class PublishedAlertController {

    private final PublishedAlertRepository publishedAlertRepository;
    private final AlertPublisherService alertPublisherService;

    /**
     * Get all published alerts
     */
    @GetMapping
    public ResponseEntity<List<PublishedAlert>> getAllAlerts() {
        return ResponseEntity.ok(publishedAlertRepository.findAll());
    }

    /**
     * Get alert by ID
     */
    @GetMapping("/{alertId}")
    public ResponseEntity<PublishedAlert> getAlertById(@PathVariable String alertId) {
        return publishedAlertRepository.findByAlertId(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get alerts by severity
     */
    @GetMapping("/severity/{severity}")
    public ResponseEntity<List<PublishedAlert>> getAlertsBySeverity(@PathVariable String severity) {
        return ResponseEntity.ok(publishedAlertRepository.findBySeverity(severity));
    }

    /**
     * Get alerts by type
     */
    @GetMapping("/type/{alertType}")
    public ResponseEntity<List<PublishedAlert>> getAlertsByType(@PathVariable String alertType) {
        return ResponseEntity.ok(publishedAlertRepository.findByAlertType(alertType));
    }

    /**
     * Get alerts within date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<PublishedAlert>> getAlertsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(publishedAlertRepository.findByPublishedAtBetween(startDate, endDate));
    }

    /**
     * Get failed alerts
     */
    @GetMapping("/failed")
    public ResponseEntity<List<PublishedAlert>> getFailedAlerts() {
        return ResponseEntity.ok(publishedAlertRepository.findFailedAlerts());
    }

    /**
     * Retry failed alerts
     */
    @PostMapping("/retry-failed")
    public ResponseEntity<Map<String, String>> retryFailedAlerts(
            @RequestParam(defaultValue = "3") int maxRetries) {
        alertPublisherService.retryFailedAlerts(maxRetries);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Retry process initiated for failed alerts"
        ));
    }

    /**
     * Get publishing statistics
     */
    @GetMapping("/statistics")
        public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        Instant since = Instant.now().minusSeconds(hours * 3600L);

        long totalPublished = publishedAlertRepository.countPublishedSince(since);
        long cellBroadcastSuccess = publishedAlertRepository.countCellBroadcastSuccessSince(since);
        long fcmSuccess = publishedAlertRepository.countFcmSuccessSince(since);

        return ResponseEntity.ok(Map.of(
            "period", hours + " hours",
            "totalPublished", totalPublished,
            "cellBroadcastSuccess", cellBroadcastSuccess,
            "fcmSuccess", fcmSuccess,
            "cellBroadcastSuccessRate", totalPublished > 0 
                ? String.format("%.2f%%", (cellBroadcastSuccess * 100.0 / totalPublished))
                : "0%",
            "fcmSuccessRate", totalPublished > 0 
                ? String.format("%.2f%%", (fcmSuccess * 100.0 / totalPublished))
                : "0%"
        ));
        }
}
