package org.fuin.cqrs4j.springboot.pm.core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Exposes operational metrics for the process-manager command outbox as Micrometer gauges: the current
 * outbox depth (commands waiting for delivery) and the number of dead-lettered commands. Being a
 * {@link MeterBinder}, it is bound automatically to every {@link MeterRegistry} the application configures;
 * if the application does not enable Micrometer, the bean is simply never bound.
 */
@ThreadSafe
public class OutboxMetrics implements MeterBinder {

    /** Gauge name for the number of commands currently waiting in the outbox. */
    public static final String OUTBOX_DEPTH = "cqrs4j.process.outbox.depth";

    /** Gauge name for the number of commands that were dead-lettered. */
    public static final String DEAD_LETTER_COUNT = "cqrs4j.process.outbox.deadletter.count";

    private final CommandOutboxService outboxService;

    /**
     * Constructor with mandatory data.
     *
     * @param outboxService Service providing the outbox and dead-letter counts.
     */
    public OutboxMetrics(final CommandOutboxService outboxService) {
        super();
        Contract.requireArgNotNull("outboxService", outboxService);
        this.outboxService = outboxService;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        Gauge.builder(OUTBOX_DEPTH, outboxService, CommandOutboxService::outboxDepth)
                .description("Number of commands currently waiting in the process-manager outbox")
                .register(registry);
        Gauge.builder(DEAD_LETTER_COUNT, outboxService, CommandOutboxService::deadLetterCount)
                .description("Number of commands that exhausted their retries and were dead-lettered")
                .register(registry);
    }

}
