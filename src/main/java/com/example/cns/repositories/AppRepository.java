package com.example.cns.repositories;

import com.example.cns.entities.App;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRepository extends JpaRepository<App, Long> {
    Optional<App> findByApiKey(String apiKey);

    boolean existsByName(String name);
}
