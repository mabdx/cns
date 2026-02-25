package com.example.cns.services;

import com.example.cns.dto.TemplateRequestDto;
import com.example.cns.dto.TemplateResponseDto;
import com.example.cns.entities.App;
import com.example.cns.entities.Template;
import com.example.cns.exception.DuplicateResourceException;
import com.example.cns.exception.InvalidOperationException;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import com.example.cns.repositories.TemplateRepository;
import com.example.cns.repositories.TemplateTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private AppRepository appRepository;

    @Mock
    private TemplateTagRepository tagRepository;

    @InjectMocks
    private TemplateService templateService;

    private App testApp;
    private TemplateRequestDto createRequest;

    @BeforeEach
    void setUp() {
        // Manually set the @Value fields for the test environment
        ReflectionTestUtils.setField(templateService, "maxSubjectLength", 50);
        ReflectionTestUtils.setField(templateService, "maxBodyLength", 500);

        // Mock the security context for createdBy/updatedBy fields
        Authentication authentication = new UsernamePasswordAuthenticationToken("testUser", "password");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        testApp = App.builder()
                .id(1L)
                .name("Test App")
                .isActive(true)
                .isDeleted(false)
                .status("ACTIVE")
                .build();

        createRequest = new TemplateRequestDto();
        createRequest.setAppId(1L);
        createRequest.setName("Test Template");
        createRequest.setSubject("Test Subject");
        createRequest.setHtmlBody("<p>Hello World</p>");
        createRequest.setStatus("ACTIVE");
    }

    @Test
    void createTemplate_ShouldCreateTemplateWithActiveStatus_WhenSuccessful() {
        // Arrange
        createRequest.setStatus("ACTIVE");
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));
        when(templateRepository.existsByAppIdAndName(1L, "Test Template")).thenReturn(false);
        when(templateRepository.saveAndFlush(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        templateService.createTemplate(createRequest);

        // Assert
        ArgumentCaptor<Template> templateCaptor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).saveAndFlush(templateCaptor.capture());
        Template capturedTemplate = templateCaptor.getValue();

        assertEquals("ACTIVE", capturedTemplate.getStatus());
        assertTrue(capturedTemplate.isActive());
    }

    @Test
    void createTemplate_ShouldCreateTemplateWithDraftStatus_WhenSpecified() {
        // Arrange
        createRequest.setStatus("DRAFT");
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));
        when(templateRepository.existsByAppIdAndName(1L, "Test Template")).thenReturn(false);
        when(templateRepository.saveAndFlush(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        templateService.createTemplate(createRequest);

        // Assert
        ArgumentCaptor<Template> templateCaptor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).saveAndFlush(templateCaptor.capture());
        Template capturedTemplate = templateCaptor.getValue();

        assertEquals("DRAFT", capturedTemplate.getStatus());
        assertFalse(capturedTemplate.isActive());
    }

    @Test
    void createTemplate_ShouldDefaultToDraftStatus_WhenStatusIsNull() {
        // Arrange
        createRequest.setStatus(null);
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));
        when(templateRepository.existsByAppIdAndName(1L, "Test Template")).thenReturn(false);
        when(templateRepository.saveAndFlush(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        templateService.createTemplate(createRequest);

        // Assert
        ArgumentCaptor<Template> templateCaptor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).saveAndFlush(templateCaptor.capture());
        Template capturedTemplate = templateCaptor.getValue();

        assertEquals("DRAFT", capturedTemplate.getStatus());
        assertFalse(capturedTemplate.isActive());
    }


    @Test
    void createTemplate_ShouldThrowException_WhenAppNotFound() {
        // Arrange
        when(appRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            templateService.createTemplate(createRequest);
        });

        assertEquals("App not found", exception.getMessage());
        verify(templateRepository, never()).saveAndFlush(any(Template.class));
    }

    @Test
    void createTemplate_ShouldThrowException_WhenTemplateNameIsDuplicate() {
        // Arrange
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));
        when(templateRepository.existsByAppIdAndName(1L, "Test Template")).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateResourceException.class, () -> {
            templateService.createTemplate(createRequest);
        });

        verify(templateRepository, never()).saveAndFlush(any(Template.class));
    }

    @Test
    void createTemplate_ShouldThrowException_WhenAppIsInactive() {
        // Arrange
        testApp.setActive(false); // Set the app to be inactive
        when(appRepository.findById(1L)).thenReturn(Optional.of(testApp));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> {
            templateService.createTemplate(createRequest);
        });

        verify(templateRepository, never()).saveAndFlush(any(Template.class));
    }

    @Test
    void createTemplate_ShouldThrowException_WhenNameIsNull() {
        // Arrange
        createRequest.setName(null); // Set the name to null

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(createRequest);
        });

        assertEquals("Template name is required", exception.getMessage());
        verify(templateRepository, never()).saveAndFlush(any(Template.class));
    }

    @Test
    void createTemplate_ShouldThrowException_WhenSubjectIsTooLong() {
        // Arrange
        createRequest.setSubject("a".repeat(51));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(createRequest);
        });
    }

    @Test
    void createTemplate_ShouldThrowException_WhenBodyIsTooLong() {
        // Arrange
        createRequest.setHtmlBody("a".repeat(501));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(createRequest);
        });
    }
}
