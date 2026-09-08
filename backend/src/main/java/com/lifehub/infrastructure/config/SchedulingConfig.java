package com.lifehub.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the background jobs.
 *
 * <p>Excluded from the {@code test} profile deliberately. The scheduler beans still exist there and
 * tests call them directly with a fixed clock (T2-10); what is switched off is the timer, so a
 * 30 second tick cannot fire halfway through an assertion and change the data under it.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
