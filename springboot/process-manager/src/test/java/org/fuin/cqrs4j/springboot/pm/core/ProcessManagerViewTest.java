package org.fuin.cqrs4j.springboot.pm.core;

import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.ProcessManagerView;
import org.fuin.cqrs4j.core.View;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for the {@link ProcessManagerView} interface (default {@code send} behaviour).
 */
@ExtendWith(MockitoExtension.class)
class ProcessManagerViewTest {

    @Mock
    private CommandOutboxService outboxService;

    @Test
    void testSendDelegatesToOutboxEnqueue() {
        // PREPARE
        final Command command = mock(Command.class);
        final ProcessManagerView testee = new TestProcessManagerView(outboxService);

        // TEST
        testee.send(command);

        // VERIFY
        verify(outboxService).enqueue(command);
    }

    /**
     * Minimal process manager view used to exercise the default {@code send} method.
     */
    private static final class TestProcessManagerView implements ProcessManagerView {

        private final CommandOutboxService outboxService;

        private TestProcessManagerView(final CommandOutboxService outboxService) {
            this.outboxService = outboxService;
        }

        @Override
        public CommandOutboxService getCommandOutboxService() {
            return outboxService;
        }

        @Override
        public String getName() {
            return "Test";
        }

        @Override
        public String getBeanName() {
            return "testProcessManagerView";
        }

        @Override
        public Class<? extends View> getBeanClass() {
            return TestProcessManagerView.class;
        }

        @Override
        public Set<EventType> getEventTypes() {
            return Set.of();
        }

        @Override
        public String getCron() {
            return "* * * * * *";
        }

        @Override
        public void handleEvents(final List<Event> events) {
            // Not used in this test
        }

    }

}
