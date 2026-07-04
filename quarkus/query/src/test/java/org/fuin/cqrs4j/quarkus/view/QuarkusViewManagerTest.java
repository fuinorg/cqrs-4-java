package org.fuin.cqrs4j.quarkus.view;

import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for the {@link QuarkusViewManager} class.
 */
@Disabled("TODO Implement!")
@QuarkusTest
@TestProfile(QuarkusViewManagerTest.class)
class QuarkusViewManagerTest implements QuarkusTestProfile {

    @Inject
    QuarkusViewManager testee;

    @InjectMock
    Scheduler scheduler;

    @InjectMock
    List<View> rawViews;

    @InjectMock
    EventStore eventstore;

    @InjectMock
    ProjectionAdminEventStore admin;

    @InjectMock
    ProjectionService projectionService;

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.hibernate-orm.default.active", "false");
    }

    @Test
    void createViews() {

        // GIVEN
        final View view = mock(View.class);
        final List<View> views = List.of(view);
        when(rawViews.stream()).thenReturn(views.stream());

        final Scheduler.JobDefinition jobDefinition = mock(Scheduler.JobDefinition.class);
        when(scheduler.newJob(view.getBeanName())).thenReturn(jobDefinition);

        // WHEN
        testee.createViews();

        // THEN
        verify(jobDefinition, times(1)).setCron(view.getCron());
        verify(jobDefinition, times(1)).setTask((Consumer<ScheduledExecution>) any());
        verify(jobDefinition, times(1)).schedule();

    }

    @Test
    void shutdownViews() {
        fail("Not yet implemented");
    }

}