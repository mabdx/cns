package com.example.cns.services;

// 1. JUnit 5 Imports (Crucial for the "Green Checks")
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// 2. Mockito Imports (The "Fake" Object tools)
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Static Imports (These make the code readable)
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 4. Your Own Project Imports
import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.entities.App;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class AppServiceTest {

    @Mock
    private AppRepository appRepository;

    @InjectMocks
    private AppService appService;

    private App testApp;

    @BeforeEach
    void setUp() {
        testApp = App.builder()
                .id(1L)
                .name("GoSaaS HR")
                .status("ACTIVE")
                .isActive(true)
                .isDeleted(false)
                .build();
    }

    @Test
    void registerApp_ShouldReturnResponse_WhenSuccessful() {
        AppRequestDto request = new AppRequestDto();
        request.setName("GoSaaS HR");

        when(appRepository.save(any(App.class))).thenReturn(testApp);

        AppResponseDto response = appService.registerApp(request);

        assertNotNull(response);
        assertEquals("GoSaaS HR", response.getName());
        verify(appRepository, times(1)).save(any(App.class));
    }

    @Test
    void archiveApp_ShouldUpdateStatus_WhenAppExists() {
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));

        appService.archiveApp(1L);

        assertEquals("ARCHIVED", testApp.getStatus());
        assertFalse(testApp.isActive());
        verify(appRepository, times(1)).save(testApp);
    }

    @Test
    void archiveApp_ShouldThrowException_WhenAppDoesNotExist() {
        when(appRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            appService.archiveApp(99L);
        });

        verify(appRepository, never()).save(any());
    }
}