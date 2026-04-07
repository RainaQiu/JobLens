package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.config.AppConfig;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Author: Raina Qiu (yuluq)
 * Runs weekly recommendation generation scaffold.
 */
@WebListener
public class WeeklySchedulerListener implements ServletContextListener {
    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        long initialDelay = secondsUntilNextRun();
        long period = TimeUnit.DAYS.toSeconds(7);

        scheduler.scheduleAtFixedRate(() -> {
            // TODO: iterate all active preferences and call recommendation pipeline.
            System.out.println("[JobLens] Weekly scheduler trigger at " + ZonedDateTime.now(ZoneOffset.UTC));
        }, initialDelay, period, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private long secondsUntilNextRun() {
        DayOfWeek targetDay = DayOfWeek.valueOf(AppConfig.get("SCHEDULE_DAY_OF_WEEK", "MONDAY"));
        int hour = Integer.parseInt(AppConfig.get("SCHEDULE_HOUR_UTC", "9"));
        int minute = Integer.parseInt(AppConfig.get("SCHEDULE_MINUTE_UTC", "0"));

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        while (!next.getDayOfWeek().equals(targetDay) || !next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).getSeconds();
    }
}
