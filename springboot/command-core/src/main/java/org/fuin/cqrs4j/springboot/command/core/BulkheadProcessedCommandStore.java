package org.fuin.cqrs4j.springboot.command.core;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.fuin.cqrs4j.core.CommandOverloadedException;
import org.fuin.cqrs4j.core.ProcessedCommandStore;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Bounds how many inbound commands may be checked against the deduplication store at the same time, so a slow
 * database or a redelivery storm sheds load instead of parking every request thread on a lookup.
 * <p>
 * The sender's outbox owns the retry, so there is deliberately <b>no retry here</b> - only isolation and fast
 * fail. A refused command is reported as {@link CommandOverloadedException}, which the REST layer turns into
 * an HTTP 503 so the sender defers and redelivers it.
 * <p>
 * <b>Only {@link #processed(String)} is guarded, never {@link #markProcessed(String)}.</b> The check runs
 * before any handler does anything, so refusing it costs nothing and the command simply arrives again. The
 * record, on the other hand, runs <em>after</em> the handler succeeded: refusing it would leave the command
 * executed but not recorded, and the next redelivery would execute it a second time. Load shedding must never
 * be able to turn an overload into a duplicate side effect, so the record always goes through.
 * <p>
 * The bulkhead is applied programmatically rather than with {@code @Bulkhead}, for the same reason as the
 * other guards in this project: Spring AOP does not intercept self-invocation or a bean the application wires
 * up itself, so the annotation would compile and silently do nothing.
 * <p>
 * A short {@code maxWait} is allowed on purpose. A brief queue absorbs the ordinary burstiness of an outbox
 * drain without turning it into a 503, while still bounding how long a request thread can be held.
 */
@ThreadSafe
public class BulkheadProcessedCommandStore implements ProcessedCommandStore {

    private static final Logger LOG = LoggerFactory.getLogger(BulkheadProcessedCommandStore.class);

    private final ProcessedCommandStore delegate;

    private final Bulkhead bulkhead;

    /**
     * Constructor with all mandatory data.
     *
     * @param delegate Store that performs the actual lookup and record.
     * @param limit    Maximum number of concurrent deduplication lookups.
     * @param maxWait  How long a caller may wait for a slot before the command is refused.
     */
    public BulkheadProcessedCommandStore(final ProcessedCommandStore delegate, final int limit,
                                         final Duration maxWait) {
        super();
        Contract.requireArgNotNull("delegate", delegate);
        Contract.requireArgNotNull("maxWait", maxWait);
        Contract.requireArgMin("limit", limit, 1);
        this.delegate = delegate;
        this.bulkhead = Bulkhead.of("cqrs4j-command-dedup", BulkheadConfig.custom()
                .maxConcurrentCalls(limit)
                .maxWaitDuration(maxWait)
                .build());
    }

    @Override
    public boolean processed(final String commandId) {
        try {
            return bulkhead.executeCallable(() -> delegate.processed(commandId));
        } catch (final BulkheadFullException ex) {
            LOG.warn("Refusing command '{}': the deduplication bulkhead is full", commandId);
            throw new CommandOverloadedException(
                    "The receiver is already handling as many commands as it is allowed to", ex);
        } catch (final RuntimeException ex) {
            throw ex;
        } catch (final Exception ex) { // NOSONAR - executeCallable declares Exception
            throw new IllegalStateException("Error checking whether command '" + commandId
                    + "' was already processed", ex);
        }
    }

    @Override
    public void markProcessed(final String commandId) {
        // Never guarded - see the class comment: the handler already ran, and refusing the record here would
        // make the next redelivery execute the command again.
        delegate.markProcessed(commandId);
    }

}
