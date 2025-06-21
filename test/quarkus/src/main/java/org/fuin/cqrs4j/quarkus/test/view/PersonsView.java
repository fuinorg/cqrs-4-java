package org.fuin.cqrs4j.quarkus.test.view;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.quarkus.test.model.AbstractPersonsView;

@Dependent
@Named(PersonsView.BEAN_NAME)
public class PersonsView extends AbstractPersonsView {

    public static final String NAME = "persons";

    public static final String BEAN_NAME = NAME + "-view";

    @Inject
    protected PersonsView(EntityManager em) {
        super(em);
    }

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
        return PersonsView.class;
    }

    @Override
    public String getCron() {
        // Every second
        return "* * * * * ?";
    }

}
