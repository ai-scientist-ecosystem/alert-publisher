package com.aiscientist.alert_publisher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO for incoming alerts from Kafka
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessage {
    
    private String alertId;
    private String severity;
    private String alertType;
    private String message;
    private String details;
    private LocalDateTime detectedAt;
    private Double latitude;
    private Double longitude;
    private Double radius;
    
    // Metadata
    private String source;
    private String dataSource;
    private Double confidence;
}
