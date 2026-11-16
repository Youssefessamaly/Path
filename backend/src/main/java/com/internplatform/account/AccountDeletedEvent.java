package com.internplatform.account;

/** Signals that queued filesystem cleanup can run after the DB transaction commits. */
public record AccountDeletedEvent() {}
