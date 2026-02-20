package com.example.cns.repositories;

import com.example.cns.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

        long countByStatus(String status);

        @Query("SELECT n FROM Notification n WHERE " +
                        "(:appId IS NULL OR n.app.id = :appId) AND " +
                        "(:templateId IS NULL OR n.template.id = :templateId) AND " +
                        "(:recipientEmail IS NULL OR LOWER(n.recipientEmail) LIKE LOWER(CONCAT('%', :recipientEmail, '%'))) AND "
                        +
                        "(:subject IS NULL OR LOWER(n.subject) LIKE LOWER(CONCAT('%', :subject, '%'))) AND " +
                        "(:status IS NULL OR LOWER(n.status) = LOWER(:status))")
        Page<Notification> findByFilters(
                        @Param("appId") Long appId,
                        @Param("templateId") Long templateId,
                        @Param("recipientEmail") String recipientEmail,
                        @Param("subject") String subject,
                        @Param("status") String status,
                        Pageable pageable);
}