package com.example.cns.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "template_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "tag_name", nullable = false)
    private String tagName;

    @Enumerated(EnumType.STRING)
    @Column(name = "datatype", nullable = false)
    @Builder.Default
    private TagDatatype datatype = TagDatatype.STRING;
}