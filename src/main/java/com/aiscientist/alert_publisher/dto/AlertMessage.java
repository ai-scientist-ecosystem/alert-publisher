package com.aiscientist.alert_publisher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for incoming alerts from Kafka (matches Alert entity from alert-engine)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertMessage {
    
    private UUID id;
    private String alertType;
    private String severity;
    
    // Space weather fields
    private Double kpValue;
    
    // Earthquake fields
    private String earthquakeId;
    private Double magnitude;
    private Double depthKm;
    private String location;
    private String region;
    
    // Tsunami fields
    private Integer tsunamiRiskScore;
    
    // Flood fields
    private String stationId;
    private String stationName;
    private Double waterLevelFeet;
    private Double floodStageFeet;
    
    // CME fields
    private Double cmeSpeed;
    private String cmeType;
    
    // Geographic coordinates
    private Double latitude;
    private Double longitude;
    
    // Common fields
    private String description;
    
    private Instant timestamp;
    
    private String rawData;
    
    private Instant createdAt;
    
    private Boolean acknowledged;
    
    private Instant acknowledgedAt;
}
