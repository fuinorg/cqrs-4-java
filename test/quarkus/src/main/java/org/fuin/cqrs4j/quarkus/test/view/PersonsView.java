package org.fuin.cqrs4j.quarkus.test.view;

import org.fuin.cqrs4j.quarkus.test.model.AbstractPersonsView;

public class PersonsView extends AbstractPersonsView {

    @Override
    public String getCron() {
        // Every second
        return "* * * * * ?";
    }

}
