package com.backend.eventsrus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// EnableAsync backs AdminNotificationEmailService's @Async methods (fire-
// and-forget admin signup alerts) - no other feature in this app needs it
// yet.
@SpringBootApplication
@EnableAsync
public class EventsrusApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventsrusApplication.class, args);
	}

}
