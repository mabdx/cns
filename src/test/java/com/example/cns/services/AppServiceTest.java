package com.example.cns.services;

import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.entities.App;
import com.example.cns.exception.DuplicateResourceException;
import com.example.cns.exception.InvalidOperationException;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AppServiceTest {

    @Mock
    private AppRepository appRepository;

    @InjectMocks
    private AppService appService;

    private App testApp;
    private AppRequestDto updateRequest;

    @BeforeEach
    void setUp() {
        // Mock the security context
        Authentication authentication = new UsernamePasswordAuthenticationToken("testUser", "password");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        testApp = App.builder()
                .id(1L)
                .name("GoSaaS HR")
                .status("ACTIVE")
                .apiKey(UUID.randomUUID().toString())
                .isActive(true)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .createdBy("testUser")
                .build();

        updateRequest = new AppRequestDto();
    }

    @Test
    void registerApp_ShouldCreateApp_WhenNameIsUnique() {
        AppRequestDto request = new AppRequestDto();
        request.setName("New App");

        when(appRepository.existsByName("New App")).thenReturn(false);
        when(appRepository.saveAndFlush(any(App.class))).thenAnswer(invocation -> {
            App app = invocation.getArgument(0);
            app.setId(2L); // Simulate saving
            return app;
        });

        AppResponseDto response = appService.registerApp(request);

        assertNotNull(response);
        assertEquals("New App", response.getName());
        assertEquals("ACTIVE", response.getStatus());
        assertNotNull(response.getApiKey());
        assertNull(response.getUpdatedAt()); // Assert updatedAt is null
        assertNull(response.getUpdatedBy()); // Assert updatedBy is null
        verify(appRepository, times(1)).saveAndFlush(any(App.class));
    }

    @Test
    void registerApp_ShouldThrowException_WhenNameExists() {
        AppRequestDto request = new AppRequestDto();
        request.setName("GoSaaS HR");

        when(appRepository.existsByName("GoSaaS HR")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> {
            appService.registerApp(request);
        });

        verify(appRepository, never()).saveAndFlush(any(App.class));
    }

    @Test
    void updateApp_ShouldUpdateName_WhenSuccessful() {
        updateRequest.setName("GoSaaS HR Updated");
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));
        when(appRepository.existsByName("GoSaaS HR Updated")).thenReturn(false);
        when(appRepository.save(any(App.class))).thenReturn(testApp);

        AppResponseDto response = appService.updateApp(1L, updateRequest);

        assertNotNull(response);
        assertEquals("GoSaaS HR Updated", response.getName());
        verify(appRepository, times(1)).save(testApp);
    }

    @Test
    void updateApp_ShouldUpdateStatusToArchived_WhenSuccessful() {
        updateRequest.setStatus("ARCHIVED");
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));
        when(appRepository.save(any(App.class))).thenReturn(testApp);

        AppResponseDto response = appService.updateApp(1L, updateRequest);

        assertNotNull(response);
        assertEquals("ARCHIVED", response.getStatus());
        assertFalse(response.getIsActive());
        verify(appRepository, times(1)).save(testApp);
    }

    @Test
    void updateApp_ShouldThrowException_WhenAppNotFound() {
        updateRequest.setName("Any Name");
        when(appRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            appService.updateApp(99L, updateRequest);
        });

        verify(appRepository, never()).save(any(App.class));
    }

    @Test
    void updateApp_ShouldThrowException_WhenStatusIsInvalid() {
        updateRequest.setStatus("INVALID_STATUS");
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));

        assertThrows(IllegalArgumentException.class, () -> {
            appService.updateApp(1L, updateRequest);
        });

        verify(appRepository, never()).save(any(App.class));
    }

    @Test
    void updateApp_ShouldThrowException_WhenAppIsAlreadyInRequestedState() {
        updateRequest.setStatus("ACTIVE"); // Same as initial state
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));

        assertThrows(InvalidOperationException.class, () -> {
            appService.updateApp(1L, updateRequest);
        });

        verify(appRepository, never()).save(any(App.class));
    }

    @Test
    void deleteApp_ShouldSetStatusToDeleted_WhenSuccessful() {
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));
        when(appRepository.save(any(App.class))).thenReturn(testApp);

        appService.deleteApp(1L);

        assertEquals("DELETED", testApp.getStatus());
        assertTrue(testApp.isDeleted());
        assertFalse(testApp.isActive());
        verify(appRepository, times(1)).save(testApp);
    }

    @Test
    void deleteApp_ShouldThrowException_WhenAppIsAlreadyDeleted() {
        testApp.setDeleted(true);
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));

        assertThrows(InvalidOperationException.class, () -> {
            appService.deleteApp(1L);
        });

        verify(appRepository, never()).save(any(App.class));
    }
}