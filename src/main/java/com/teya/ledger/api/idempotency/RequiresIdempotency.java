package com.teya.ledger.api.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller handler method that participates in idempotency
 * processing: missing/blank header → 400, repeat with same body →
 * replay cached response, repeat with different body → 409.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresIdempotency {
}
