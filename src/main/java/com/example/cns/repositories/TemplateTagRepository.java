package com.example.cns.repositories;

import com.example.cns.entities.TemplateTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TemplateTagRepository extends JpaRepository<TemplateTag, Long> {
    List<TemplateTag> findByTemplateId(Long templateId);
    void deleteByTemplateId(Long templateId); // Useful for updates
}