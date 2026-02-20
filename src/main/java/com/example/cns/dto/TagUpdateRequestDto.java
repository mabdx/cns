package com.example.cns.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.Map;

@Data
public class TagUpdateRequestDto {
    @NotEmpty(message = "tagTypes map cannot be empty")
    private Map<String, String> tagTypes; // Tag Name -> Datatype (STRING, NUMBER, BOOLEAN)
}
