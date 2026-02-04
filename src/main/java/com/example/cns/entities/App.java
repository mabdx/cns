package com.example.cns.entities;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "apps")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class App {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String apiKey;

    private String status; // e.g., "ACTIVE", "RETIRED"

    private boolean isActive = true;

    private boolean isDeleted = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private String createdBy; // Name/username of the System Manager
}
