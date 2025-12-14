package com.aiscientist.alert_publisher.repository;

import com.aiscientist.alert_publisher.model.PublishedAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;



import com.aiscientist.alert_publisher.model.PublishedAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublishedAlertRepository extends JpaRepository<PublishedAlert, Long> {

    Optional<PublishedAlert> findByAlertId(String alertId);

    List<PublishedAlert> findBySeverity(String severity);

    List<PublishedAlert> findByAlertType(String alertType);


       @Query("SELECT p FROM PublishedAlert p WHERE p.publishedAt BETWEEN :startDate AND :endDate")
       List<PublishedAlert> findByPublishedAtBetween(Instant startDate, Instant endDate);

    @Query("SELECT p FROM PublishedAlert p WHERE p.cellBroadcastStatus = 'FAILED' OR p.fcmStatus = 'FAILED'")
    List<PublishedAlert> findFailedAlerts();

    @Query("SELECT p FROM PublishedAlert p WHERE " +
           "(p.cellBroadcastStatus = 'FAILED' OR p.fcmStatus = 'FAILED') " +
           "AND p.retryCount < :maxRetries")
    List<PublishedAlert> findAlertsForRetry(int maxRetries);


       @Query("SELECT COUNT(p) FROM PublishedAlert p WHERE p.publishedAt > :since")
       Long countPublishedSince(Instant since);

    @Query("SELECT COUNT(p) FROM PublishedAlert p WHERE " +
           "p.cellBroadcastStatus = 'SUCCESS' AND p.publishedAt > :since")
    Long countCellBroadcastSuccessSince(Instant since);

    @Query("SELECT COUNT(p) FROM PublishedAlert p WHERE " +
           "p.fcmStatus = 'SUCCESS' AND p.publishedAt > :since")
    Long countFcmSuccessSince(Instant since);
}
