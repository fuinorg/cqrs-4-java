package org.fuin.cqrs4j.quarkus.base;

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import org.fuin.cqrs4j.core.View;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.TenantId;

import java.util.List;
import java.util.Set;

@Dependent
@Named(TestView.BEAN_NAME)
public class TestView implements View {

    public static final String NAME = "test";

    public static final String BEAN_NAME = NAME + "-view";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getBeanName() {
        return BEAN_NAME;
    }

    @Override
    public Class<? extends View> getBeanClass() {
        return TestView.class;
    }

    @Override
    public Set<EventType> getEventTypes() {
        return Set.of();
    }

    @Override
    public String getCron() {
        return "";
    }

    @Override
    public void handleEvents(@Nullable TenantId tenantId, List<Event> events) {
    }

}
