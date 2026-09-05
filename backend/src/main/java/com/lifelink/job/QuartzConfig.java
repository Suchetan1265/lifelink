package com.lifelink.job;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;
import java.time.Duration;
import java.time.Instant;

/**
 * Schedules the maintenance jobs (spec §2.E). The store is in-memory, so
 * triggers are rebuilt on every boot and no scheduler tables are needed.
 */
@Configuration
public class QuartzConfig {

    private static final int MAINTENANCE_INTERVAL_MINUTES = 15;

    /** Keeps startup clear of the first firing, which matters most in tests. */
    private static final Duration STARTUP_GRACE = Duration.ofMinutes(1);

    @Bean
    JobDetail requestEscalationJobDetail() {
        return JobBuilder.newJob(RequestEscalationJob.class)
                .withIdentity("requestEscalation")
                .withDescription("Escalate requests past their urgency deadline")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger requestEscalationTrigger(JobDetail requestEscalationJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(requestEscalationJobDetail)
                .withIdentity("requestEscalationTrigger")
                .startAt(Date.from(Instant.now().plus(STARTUP_GRACE)))
                .withSchedule(everyFifteenMinutes())
                .build();
    }

    @Bean
    JobDetail requestExpiryJobDetail() {
        return JobBuilder.newJob(RequestExpiryJob.class)
                .withIdentity("requestExpiry")
                .withDescription("Expire requests past their needed-by time")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger requestExpiryTrigger(JobDetail requestExpiryJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(requestExpiryJobDetail)
                .withIdentity("requestExpiryTrigger")
                .startAt(Date.from(Instant.now().plus(STARTUP_GRACE)))
                .withSchedule(everyFifteenMinutes())
                .build();
    }

    @Bean
    JobDetail donorEligibilityJobDetail() {
        return JobBuilder.newJob(DonorEligibilityJob.class)
                .withIdentity("donorEligibility")
                .withDescription("Notify donors whose 90-day cooldown ended")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger donorEligibilityTrigger(JobDetail donorEligibilityJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(donorEligibilityJobDetail)
                .withIdentity("donorEligibilityTrigger")
                .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(0, 5))
                .build();
    }

    /**
     * A missed window (downtime, a long-running previous run) is picked up on
     * the next tick rather than replayed, since these jobs are catch-up by
     * nature: they re-read whatever is still overdue.
     */
    private static SimpleScheduleBuilder everyFifteenMinutes() {
        return SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(MAINTENANCE_INTERVAL_MINUTES)
                .repeatForever()
                .withMisfireHandlingInstructionNextWithRemainingCount();
    }
}
