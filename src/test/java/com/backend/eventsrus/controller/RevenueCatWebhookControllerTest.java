package com.backend.eventsrus.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.backend.eventsrus.config.RevenueCatProperties;
import com.backend.eventsrus.service.RevenueCatWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RevenueCatWebhookControllerTest {

    @Mock
    private RevenueCatWebhookService service;

    private RevenueCatProperties properties;
    private RevenueCatWebhookController controller;

    @BeforeEach
    void setUp() {
        properties = new RevenueCatProperties();
        controller = new RevenueCatWebhookController(properties, service, JsonMapper.builder().build());
    }

    @Test
    void rejectsEverythingWhenSecretNotConfigured() {
        properties.setWebhookSecret("");
        assertThat(controller.receive("{}", "anything").getStatusCode().value()).isEqualTo(401);
        verify(service, never()).handle(any());
    }

    @Test
    void rejectsMissingOrWrongHeader() {
        properties.setWebhookSecret("s3cret");
        assertThat(controller.receive("{}", null).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.receive("{}", "wrong").getStatusCode().value()).isEqualTo(401);
        verify(service, never()).handle(any());
    }

    @Test
    void acceptsRawOrBearerSecret() {
        properties.setWebhookSecret("s3cret");
        assertThat(controller.receive("{}", "s3cret").getStatusCode().value()).isEqualTo(200);
        assertThat(controller.receive("{}", "Bearer s3cret").getStatusCode().value()).isEqualTo(200);
    }
}
