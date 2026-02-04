package com.example.cns.repositories;

import com.example.cns.entities.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    List<Template> findByAppIdAndStatus(Long appId, String status);
}
