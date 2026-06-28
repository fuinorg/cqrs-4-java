package org.fuin.cqrs4j.pm;

import org.fuin.cqrs4j.core.Command;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link SimpleCommandBus}.
 */
class SimpleCommandBusTest {

    @Test
    void testSendRoutesToRegisteredHandler() {

        // PREPARE
        final SimpleCommandBus bus = new SimpleCommandBus();
        final AtomicReference<Command> received = new AtomicReference<>();
        bus.register(ReserveStockCommand.class, received::set);
        final ReserveStockCommand command = new ReserveStockCommand(new SampleProcessId());

        // TEST
        bus.send(command);

        // VERIFY
        assertThat(received.get()).isSameAs(command);
    }

    @Test
    void testSendUnknownCommandThrows() {

        // PREPARE
        final SimpleCommandBus bus = new SimpleCommandBus();

        // TEST & VERIFY
        assertThatThrownBy(() -> bus.send(new ReserveStockCommand(new SampleProcessId())))
                .isInstanceOf(IllegalStateException.class);
    }

}
