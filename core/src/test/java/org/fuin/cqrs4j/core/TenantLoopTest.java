package org.fuin.cqrs4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.ThreadLocalTenantContext;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TenantLoop}.
 */
public class TenantLoopTest {

    private static final TenantId ACME = new TenantId("acme");

    private static final TenantId BETA = new TenantId("beta");

    @AfterEach
    public void clearTenant() {
        new ThreadLocalTenantContext().clear();
    }

    @Test
    public void testWorkSeesEachTenantInTurn() {

        final WritableTenantContext context = new ThreadLocalTenantContext();
        final List<String> seen = new ArrayList<>();

        TenantLoop.run(context, () -> Stream.of(ACME, BETA),
                () -> seen.add(context.getTenantId().map(TenantId::asString).orElse("<none>")));

        assertThat(seen).containsExactly("acme", "beta");
    }

    @Test
    public void testTheTenantIsClearedAfterwards() {

        // These run on pooled scheduler threads. A tenant left on the thread is inherited by whatever runs
        // next, which is how one tenant's data ends up written into another's.
        final WritableTenantContext context = new ThreadLocalTenantContext();

        TenantLoop.run(context, () -> Stream.of(ACME), () -> { /* nothing */ });

        assertThat(context.getTenantId()).isEmpty();
    }

    @Test
    public void testOneTenantFailingDoesNotStopTheRest() {

        // An unreachable stream or a locked table for one tenant must not starve every other tenant.
        final WritableTenantContext context = new ThreadLocalTenantContext();
        final List<String> seen = new ArrayList<>();

        TenantLoop.run(context, () -> Stream.of(ACME, BETA), () -> {
            final String tenant = context.getTenantId().map(TenantId::asString).orElseThrow();
            if ("acme".equals(tenant)) {
                throw new IllegalStateException("acme is having a bad minute");
            }
            seen.add(tenant);
        });

        assertThat(seen).containsExactly("beta");
        assertThat(context.getTenantId()).isEmpty();
    }

    @Test
    public void testSingleTenantRunsOnceWithNothingOnTheThread() {

        final List<String> seen = new ArrayList<>();

        TenantLoop.run(null, null, () -> seen.add("ran"));

        assertThat(seen).containsExactly("ran");
        assertThat(new ThreadLocalTenantContext().getTenantId()).isEmpty();
    }

}
