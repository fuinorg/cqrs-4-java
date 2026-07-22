package org.fuin.cqrs4j.esc;

import org.fuin.cqrs4j.core.CqrsUtils;
import org.fuin.esc.api.EscConnectionException;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

/**
 * Event store related helper functions.
 */
@ThreadSafe
public final class EscUtils {

    private EscUtils() {
        throw new UnsupportedOperationException("Utility classes cannot be instantiated");
    }

    /**
     * Determines if the given error is a transient infrastructure failure that is expected to self-heal, as
     * opposed to an unexpected programming or configuration error that should be surfaced.
     * <p>
     * This is the event store aware variant of {@link CqrsUtils#isTransientInfrastructureFailure(Throwable)}:
     * the event store reports every "store or database not reachable" condition as an
     * {@link EscConnectionException} (a connection problem, a call that ran into its timeout, or the store
     * answering that it is unavailable), so a single {@code instanceof} settles those. Everything else falls
     * back to the framework neutral classification, which still catches failures raised below the event store
     * abstraction, for example a JDBC or gRPC error surfacing directly.
     * <p>
     * Use this wherever an event store is involved; use the {@code CqrsUtils} variant where it is not.
     *
     * @param error Error to classify (may be {@literal null}).
     *
     * @return {@literal true} if the error is a transient infrastructure failure.
     */
    public static boolean isTransientInfrastructureFailure(@Nullable final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof EscConnectionException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return CqrsUtils.isTransientInfrastructureFailure(error);
    }

}
