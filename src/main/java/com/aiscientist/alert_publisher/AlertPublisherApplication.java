package com.aiscientist.alert_publisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Alert Publisher Application
 * 
 * Consumes alerts from Kafka and publishes them via:
 * - Cell Broadcast (Telecom API)
 * - Push Notifications (Firebase Cloud Messaging)
 * 
 * @author AI Scientist Ecosystem
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@EnableAsync
public class AlertPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertPublisherApplication.class, args);
    }
}
