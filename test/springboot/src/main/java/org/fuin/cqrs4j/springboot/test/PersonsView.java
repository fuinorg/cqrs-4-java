package org.fuin.cqrs4j.springboot.test;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.test.model.AbstractPersonsView;

public class PersonsView extends AbstractPersonsView {

    public PersonsView(EntityManager em) {
        super(em);
    }

    @Override
    public String getCron() {
        // Every second
        return "* * * * * *";
    }

}
