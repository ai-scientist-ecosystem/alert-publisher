package com.aiscientist.alert_publisher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing an alert to be published
 */
@Entity
@Table(name = "published_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String alertId;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String alertType;

    @Column(length = 2000)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    @Column(nullable = false)
    private LocalDateTime publishedAt;

    // Publishing status
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PublishStatus cellBroadcastStatus = PublishStatus.PENDING;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PublishStatus fcmStatus = PublishStatus.PENDING;

    @Column
    private String cellBroadcastMessageId;

    @Column
    private String fcmMessageId;

    @Column
    private Integer recipientCount = 0;

    @Column
    private Integer successCount = 0;

    @Column
    private Integer failureCount = 0;

    @Column(length = 2000)
    private String errorMessage;

    @Column
    private Integer retryCount = 0;

    @Column
    private LocalDateTime lastRetryAt;

    public enum PublishStatus {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        SKIPPED
    }
}
