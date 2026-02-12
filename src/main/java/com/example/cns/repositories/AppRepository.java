package com.example.cns.repositories;

import com.example.cns.entities.App;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRepository extends JpaRepository<App, Long> {
    Optional<App> findByApiKey(String apiKey);

    boolean existsByName(String name);

    // Pagination support
    org.springframework.data.domain.Page<App> findAll(org.springframework.data.domain.Pageable pageable);

    // Filtering with pagination
    @org.springframework.data.jpa.repository.Query("SELECT a FROM App a WHERE " +
            "(:id IS NULL OR a.id = :id) AND " +
            "(:name IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "a.isDeleted = false")
    org.springframework.data.domain.Page<App> findByFilters(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("name") String name,
            org.springframework.data.domain.Pageable pageable);
}
