package com.example.cns.services;

import com.example.cns.dto.NotificationRequestDto;
import com.example.cns.entities.*;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import com.example.cns.repositories.NotificationRepository;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NotificationServiceTest {

    @Mock
    private AppRepository appRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TemplateTagRepository tagRepository;

    @InjectMocks
    private NotificationService notificationService;

    private App testApp;
    private Template testTemplate;
    private NotificationRequestDto notificationRequest;

    @BeforeEach
    void setUp() {
        testApp = App.builder()
                .id(1L)
                .apiKey("test-api-key")
                .status("ACTIVE")
                .isDeleted(false)
                .build();

        testTemplate = Template.builder()
                .id(100L)
                .app(testApp)
                .status("ACTIVE")
                .subject("Hello {{name}}")
                .htmlBody("<p>Your price is {{price}}.</p>")
                .build();

        notificationRequest = new NotificationRequestDto();
        notificationRequest.setApiKey("test-api-key");
        notificationRequest.setTemplateId(100L);
        notificationRequest.setRecipient("test@example.com");
    }

    @Test
    void sendNotification_ShouldSucceed_WhenRequestIsValid() {
        // Arrange
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("name", "John");
        placeholders.put("price", 123.45);
        notificationRequest.setPlaceholders(placeholders);

        when(appRepository.findByApiKey("test-api-key")).thenReturn(Optional.of(testApp));
        when(templateRepository.findById(100L)).thenReturn(Optional.of(testTemplate));
        when(tagRepository.findByTemplateId(100L)).thenReturn(Arrays.asList(
                TemplateTag.builder().tagName("name").datatype(TagDatatype.STRING).build(),
                TemplateTag.builder().tagName("price").datatype(TagDatatype.NUMBER).build()
        ));

        // Act
        notificationService.sendNotification(notificationRequest);

        // Assert
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());

        Notification savedNotification = notificationCaptor.getValue();
        assertEquals("SENT", savedNotification.getStatus());
        assertEquals("test@example.com", savedNotification.getRecipientEmail());
        assertEquals("Hello John", savedNotification.getSubject());
        assertEquals("<p>Your price is 123.45.</p>", savedNotification.getBody());
    }

    @Test
    void sendNotification_ShouldThrowSecurityException_WhenApiKeyIsInvalid() {
        // Arrange
        when(appRepository.findByApiKey("test-api-key")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(SecurityException.class, () -> {
            notificationService.sendNotification(notificationRequest);
        });

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void sendNotification_ShouldThrowResourceNotFoundException_WhenTemplateNotFound() {
        // Arrange
        when(appRepository.findByApiKey("test-api-key")).thenReturn(Optional.of(testApp));
        when(templateRepository.findById(100L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            notificationService.sendNotification(notificationRequest);
        });
    }

    @Test
    void sendNotification_ShouldThrowIllegalStateException_WhenTemplateIsNotActive() {
        // Arrange
        testTemplate.setStatus("DRAFT");
        when(appRepository.findByApiKey("test-api-key")).thenReturn(Optional.of(testApp));
        when(templateRepository.findById(100L)).thenReturn(Optional.of(testTemplate));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            notificationService.sendNotification(notificationRequest);
        });
    }

    @Test
    void sendNotification_ShouldThrowIllegalArgumentException_WhenMissingRequiredTags() {
        // Arrange
        notificationRequest.setPlaceholders(Collections.singletonMap("name", "John")); // Missing 'price'

        when(appRepository.findByApiKey("test-api-key")).thenReturn(Optional.of(testApp));
        when(templateRepository.findById(100L)).thenReturn(Optional.of(testTemplate));
        when(tagRepository.findByTemplateId(100L)).thenReturn(Arrays.asList(
                TemplateTag.builder().tagName("name").datatype(TagDatatype.STRING).build(),
                TemplateTag.builder().tagName("price").datatype(TagDatatype.NUMBER).build()
        ));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            notificationService.sendNotification(notificationRequest);
        });

        assertTrue(exception.getMessage().contains("Missing required tags"));
    }

    @Test
    void sendNotification_ShouldThrowIllegalArgumentException_WhenNoRecipientsProvided() {
        // Arrange
        notificationRequest.setRecipient(null);
        notificationRequest.setRecipients(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            notificationService.sendNotification(notificationRequest);
        });

        assertEquals("At least one recipient must be provided", exception.getMessage());
    }
}
