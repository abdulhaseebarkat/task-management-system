package com.slmtires.itms.dto;

import java.time.Instant;

/** A deliberately safe, human-readable item in a task's activity timeline. */
public record TaskActivityResponse(Long id, Instant occurredAt, String type, String message, String actorName) {}
