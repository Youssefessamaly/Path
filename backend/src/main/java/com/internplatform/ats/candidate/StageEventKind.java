package com.internplatform.ats.candidate;

/**
 * ENTERED is the only kind this phase ever produces — automatic intake on a new submission, or
 * lazy backfill of a pre-existing APPLIED application (see AtsApplicationIntakeService). MOVED
 * is reserved for the future candidate-movement phase; no code in this phase creates one.
 */
public enum StageEventKind {
    ENTERED,
    MOVED
}
