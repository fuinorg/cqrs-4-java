package org.fuin.cqrs4j.quarkus.test.app;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.fuin.cqrs4j.quarkus.test.model.PersonEntity;
import org.fuin.cqrs4j.quarkus.test.model.PersonId;

/**
 * REST resource reading persons.
 */
@Path("/persons")
@Transactional
public class PersonResource {

    @Inject
    EntityManager em;

    @GET
    @Path("{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response read(@PathParam("id") PersonId id) {
        final PersonEntity person = em.find(PersonEntity.class, id.asBaseType());
        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(person).build();
    }

}
