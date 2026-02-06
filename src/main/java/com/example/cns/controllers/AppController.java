package com.example.cns.controllers;

import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.services.AppService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;


@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppController {
    private final AppService appService;

    @PostMapping("/register")
    public ResponseEntity<AppResponseDto> resister(@Valid @RequestBody AppRequestDto request) {
        return ResponseEntity.ok(appService.registerApp(request));
    }

    @GetMapping
    public ResponseEntity<List<AppResponseDto>> getAllApps() {
        return ResponseEntity.ok(appService.getAllApps());
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<String> archiveApp(@PathVariable Long id) {
        appService.archiveApp(id);
        return ResponseEntity.ok("App archived successfully");
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteApp(@PathVariable Long id) {
        appService.deleteApp(id);
        return ResponseEntity.ok("App deleted successfully");
    }
}
