package com.rta.dignify.dto.cron;

public record CronJobResult(
        int collectedCount,
        long lastProcessedId,
        long durationSeconds
) { }