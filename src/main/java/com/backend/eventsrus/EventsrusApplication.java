package com.backend.eventsrus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableAsync backs AdminNotificationEmailService's @Async methods (fire-
// and-forget admin signup alerts). EnableScheduling backs
// AccountDeletionScheduler's daily sweep.
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EventsrusApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventsrusApplication.class, args);
	}

}
