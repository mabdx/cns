package com.example.cns.repositories;

import com.example.cns.entities.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateRepository extends JpaRepository<Template, Long> {
        List<Template> findByAppId(Long appId);

        List<Template> findByAppIdAndStatus(Long appId, String status);

        boolean existsByAppIdAndName(Long appId, String name);

        // Support pagination
        org.springframework.data.domain.Page<Template> findAll(org.springframework.data.domain.Pageable pageable);

        // Support filtering with optional parameters (handled via query or service)
        // For now, let's keep it simple and add specific combinations or use
        // Specification
        // But to fix BUG_20 (filter by app OR status), we can use a custom query or
        // JpaSpecification.
        // Let's use a custom query for simplicity if comfortable, or just multiple
        // methods.

        // Actually, to support "AppId AND/OR Status", a Query is best.
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Template t WHERE " +
                        "(:appId IS NULL OR t.app.id = :appId) AND " +
                        "(:status IS NULL OR t.status = :status) AND " +
                        "t.isDeleted = false")
        org.springframework.data.domain.Page<Template> findByAppIdAndStatus(
                        @org.springframework.data.repository.query.Param("appId") Long appId,
                        @org.springframework.data.repository.query.Param("status") String status,
                        org.springframework.data.domain.Pageable pageable);

}
