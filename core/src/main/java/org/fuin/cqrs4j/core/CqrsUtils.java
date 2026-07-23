package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EventType;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.Adler32;

/**
 * CQRS related helper functions.
 */
@ThreadSafe
public final class CqrsUtils {

    private CqrsUtils() {
        throw new UnsupportedOperationException("Utility classes cannot be instantiated");
    }

    /**
     * Creates an Adler32 checksum based on event type names.
     *
     * @param eventTypes Types to calculate a checksum for.
     * @return Checksum based on all names.
     */
    public static long calculateAdler32Checksum(final Collection<EventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes cannot be null or empty");
        }
        final List<EventType> sortedList = new ArrayList<>(eventTypes);
        Collections.sort(sortedList);
        final Adler32 checksum = new Adler32();
        for (final EventType eventType : sortedList) {
            checksum.update(eventType.asBaseType().getBytes(StandardCharsets.US_ASCII));
        }
        return checksum.getValue();
    }

    /**
     * Calculates an Adler32 checksum over a view's selection (event types <em>and</em> categories) so that
     * two views with a different selection get distinct projection stream identities. Either set may be empty
     * but not both.
     *
     * @param eventTypes Event types the view selects (may be empty).
     * @param categories Category names the view selects (may be empty).
     *
     * @return Checksum.
     */
    public static long calculateAdler32Checksum(final Collection<EventType> eventTypes,
                                                final Collection<String> categories) {
        final boolean noTypes = eventTypes == null || eventTypes.isEmpty();
        final boolean noCategories = categories == null || categories.isEmpty();
        if (noTypes && noCategories) {
            throw new IllegalArgumentException("eventTypes and categories cannot both be null or empty");
        }
        final Adler32 checksum = new Adler32();
        if (!noTypes) {
            final List<String> sortedTypes = new ArrayList<>();
            for (final EventType eventType : eventTypes) {
                sortedTypes.add(eventType.asBaseType());
            }
            Collections.sort(sortedTypes);
            for (final String type : sortedTypes) {
                checksum.update(type.getBytes(StandardCharsets.US_ASCII));
            }
        }
        // Separator so that {types=[A], categories=[]} and {types=[], categories=[A]} differ.
        checksum.update('|');
        if (!noCategories) {
            final List<String> sortedCategories = new ArrayList<>(categories);
            Collections.sort(sortedCategories);
            for (final String category : sortedCategories) {
                checksum.update(category.getBytes(StandardCharsets.US_ASCII));
            }
        }
        return checksum.getValue();
    }

    /**
     * Failures that live in the packages matched below but are <em>answers about the data</em>, not signs
     * that the database could not be reached. A concurrency conflict, a constraint violation or a missing row
     * will still be there on the next attempt, so treating them as transient would retry them forever, log
     * them at {@code DEBUG} as if they were expected, and - worst - open the projection circuit breaker for
     * every other view because of one broken write.
     * <p>
     * Matched by name so this class stays free of any JPA or Spring dependency, like the prefixes below.
     */
    private static final Set<String> PERMANENT_DATA_FAILURES = Set.of(
            // JPA
            "jakarta.persistence.OptimisticLockException",
            "jakarta.persistence.EntityExistsException",
            "jakarta.persistence.EntityNotFoundException",
            "jakarta.persistence.NoResultException",
            "jakarta.persistence.NonUniqueResultException",
            "jakarta.persistence.RollbackException",
            // JDBC
            "java.sql.SQLIntegrityConstraintViolationException",
            "java.sql.SQLSyntaxErrorException",
            "java.sql.SQLFeatureNotSupportedException",
            // Spring
            "org.springframework.dao.OptimisticLockingFailureException",
            "org.springframework.dao.DuplicateKeyException",
            "org.springframework.dao.DataIntegrityViolationException",
            "org.springframework.dao.EmptyResultDataAccessException",
            "org.springframework.dao.IncorrectResultSizeDataAccessException",
            "org.springframework.dao.InvalidDataAccessApiUsageException");

    /**
     * Determines if the given error looks like a transient infrastructure connectivity failure (event store
     * transport error, call timeout, socket/IO error, or a JDBC/JPA connection problem) that is expected to
     * self-heal, as opposed to an unexpected programming or configuration error that should be surfaced.
     * <p>
     * The distinction matters wherever a failure is retried on a schedule: a transient failure is a normal
     * part of operating against remote infrastructure and should not be logged as an error on every attempt,
     * while an unexpected error must stay visible instead of being hidden behind a "cannot reach the store"
     * message. It is also the predicate to use for retry and circuit breaker policies.
     * <p>
     * The whole cause chain is inspected, because the frameworks wrap the original failure several times.
     * Types are matched by name so that this stays free of any framework or event store dependency.
     *
     * @param error Error to classify (may be {@literal null}).
     *
     * @return {@literal true} if the error is a transient infrastructure failure.
     */
    public static boolean isTransientInfrastructureFailure(@Nullable final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TransientCommandDeliveryException) {
                return true;
            }
            if (t instanceof CommandDeliveryException) {
                // Permanent: the endpoint answered and the command itself is the problem.
                return false;
            }
            if (t instanceof java.io.IOException) {
                return true;
            }
            // A remote call that ran into its timeout (esc wraps the TimeoutException). The store did not
            // answer in time, which is the same class of problem as not reaching it at all.
            if (t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            final String type = t.getClass().getName();
            if (PERMANENT_DATA_FAILURES.contains(type)) {
                // The database answered, and the answer is about the data rather than about reaching it.
                // Retrying cannot change it, so it must not be hidden behind a "will retry" debug line and
                // must never open a circuit breaker for everyone else.
                return false;
            }
            if (type.startsWith("io.grpc.")                       // gRPC transport / StatusRuntimeException
                    || type.startsWith("java.net.")               // ConnectException, SocketException, ...
                    || type.startsWith("java.sql.")               // SQLException / transient DB errors
                    || type.startsWith("jakarta.persistence.")    // JPA persistence exceptions on a DB hiccup
                    || type.startsWith("org.springframework.dao.")) { // Spring's DataAccessException hierarchy
                return true;
            }
        }
        return false;
    }

}
