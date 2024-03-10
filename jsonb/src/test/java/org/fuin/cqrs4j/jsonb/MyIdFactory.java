package org.fuin.cqrs4j.jsonb;

import com.tngtech.archunit.junit.ArchIgnore;
import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.EntityIdFactory;

@ArchIgnore
final class MyIdFactory implements EntityIdFactory {
    @Override
    public EntityId createEntityId(final String type, final String id) {
        if (type.equals("A")) {
            return new AId(Long.valueOf(id));
        }
        if (type.equals("B")) {
            return new BId(Long.valueOf(id));
        }
        if (type.equals("C")) {
            return new CId(Long.valueOf(id));
        }
        throw new IllegalArgumentException("Unknown type: '" + type + "'");
    }

    @Override
    public boolean containsType(final String type) {
        if (type.equals("A")) {
            return true;
        }
        if (type.equals("B")) {
            return true;
        }
        if (type.equals("C")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isValid(String type, String id) {
        return true;
    }
}
