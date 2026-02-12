package com.example.cns.repositories;

import com.example.cns.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE " +
            "(:templateId IS NULL OR n.template.id = :templateId) AND " +
            "(:recipientEmail IS NULL OR n.recipientEmail LIKE %:recipientEmail%) AND " +
            "(:status IS NULL OR n.status = :status)")
    Page<Notification> findByFilters(
            @Param("templateId") Long templateId,
            @Param("recipientEmail") String recipientEmail,
            @Param("status") String status,
            Pageable pageable);
}