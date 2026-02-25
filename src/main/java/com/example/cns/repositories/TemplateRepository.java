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


        // Actually, to support "AppId AND/OR Status", a Query is best.
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Template t WHERE " +
                        "(:appId IS NULL OR t.app.id = :appId) AND " +
                        "(:status IS NULL OR t.status = :status) AND " +
                        "(:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
                        "(:includeDeleted = true OR t.isDeleted = false)")
        org.springframework.data.domain.Page<Template> findByAppIdAndStatus(
                        @org.springframework.data.repository.query.Param("appId") Long appId,
                        @org.springframework.data.repository.query.Param("status") String status,
                        @org.springframework.data.repository.query.Param("name") String name,
                        @org.springframework.data.repository.query.Param("includeDeleted") Boolean includeDeleted,
                        org.springframework.data.domain.Pageable pageable);

}
