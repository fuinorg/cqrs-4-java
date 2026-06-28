package org.fuin.cqrs4j.pm;

import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.esc.EventStoreRepositoryAsync;
import org.fuin.esc.api.EventStoreAsync;
import org.fuin.utils4j.TestOmitted;

/**
 * Async event-sourced repository for the sample process manager (concrete subclass of the ddd4j
 * {@link EventStoreRepositoryAsync}).
 */
@TestOmitted("Only a test class")
final class SampleProcessManagerRepository extends EventStoreRepositoryAsync<SampleProcessId, SampleProcessManager> {

    SampleProcessManagerRepository(final EventStoreAsync eventStore) {
        super(eventStore);
    }

    @Override
    public Class<SampleProcessManager> getAggregateClass() {
        return SampleProcessManager.class;
    }

    @Override
    public EntityType getAggregateType() {
        return SampleProcessId.TYPE;
    }

    @Override
    public SampleProcessManager create() {
        return new SampleProcessManager();
    }

    @Override
    protected String getIdParamName() {
        return "sampleProcessId";
    }

}
