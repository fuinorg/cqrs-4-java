package org.fuin.cqrs4j.quarkus.test.view;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.quarkus.test.model.AbstractPersonsView;

public class PersonsView extends AbstractPersonsView {

    public PersonsView(EntityManager em) {
        super(em);
    }

    @Override
    public String getCron() {
        // Every second
        return "* * * * * ?";
    }

}
