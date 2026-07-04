package org.fuin.cqrs4j.quarkus.pm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Exposes operational metrics for the process-timeout registry as Micrometer gauges: the number of pending
 * timeouts and the number that are already overdue (deadline reached but not yet handled - a rising overdue count
 * signals stuck processes or a missing/failing timeout handler). Being a {@link MeterBinder} CDI bean, it is
 * bound automatically by the Quarkus Micrometer extension; applications without Micrometer are unaffected.
 */
@ThreadSafe
@Singleton
public class ProcessTimeoutMetrics implements MeterBinder {

    /** Gauge name for the number of pending process timeouts. */
    public static final String PENDING = "cqrs4j.process.timeout.pending";

    /** Gauge name for the number of overdue process timeouts (deadline reached, not yet handled). */
    public static final String OVERDUE = "cqrs4j.process.timeout.overdue";

    private final ProcessTimeoutRepository repository;

    /**
     * Constructor with mandatory data.
     *
     * @param repository Repository providing the pending and overdue counts.
     */
    @Inject
    public ProcessTimeoutMetrics(final ProcessTimeoutRepository repository) {
        super();
        Contract.requireArgNotNull("repository", repository);
        this.repository = repository;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        Gauge.builder(PENDING, repository, ProcessTimeoutRepository::pendingCount)
                .description("Number of pending process timeouts")
                .register(registry);
        Gauge.builder(OVERDUE, repository, repo -> repo.overdueCount(System.currentTimeMillis()))
                .description("Number of overdue process timeouts (deadline reached but not yet handled)")
                .register(registry);
    }

}
