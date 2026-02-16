package com.example.cns.repositories;

import com.example.cns.entities.App;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppRepository extends JpaRepository<App, Long> {
    Optional<App> findByApiKey(String apiKey);

    boolean existsByName(String name);

    Optional<App> findByName(String name);

    // Filtering with pagination
    @Query("SELECT a FROM App a WHERE " +
            "(:id IS NULL OR a.id = :id) AND " +
            "(:name IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:status IS NULL AND a.isDeleted = false OR LOWER(a.status) = LOWER(:status))")
    Page<App> findByFilters(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("status") String status,
            Pageable pageable);
}