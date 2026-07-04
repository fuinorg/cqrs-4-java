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
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.cqrs4j.quarkus.test.model.PersonEntity;
import org.fuin.cqrs4j.quarkus.test.model.PersonId;
import org.fuin.cqrs4j.quarkus.test.view.PersonsView;
import org.fuin.cqrs4j.quarkus.view.QuarkusProjectionFreshnessService;

/**
 * REST resource reading persons.
 */
@ThreadSafe
@Path("/persons")
@Transactional
public class PersonResource {

    /** Response header advertising the projection position the read model is current as of. */
    public static final String PROJECTION_POSITION_HEADER = "X-Projection-Position";

    @Inject
    EntityManager em;

    @Inject
    QuarkusProjectionFreshnessService freshnessService;

    @GET
    @Path("{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response read(@PathParam("id") PersonId id) {
        final PersonEntity person = em.find(PersonEntity.class, id.asBaseType());
        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // Advertise how fresh this read model is: the projection position it has consumed up to.
        return Response.ok(person)
                .header(PROJECTION_POSITION_HEADER, freshnessService.position(PersonsView.NAME))
                .build();
    }

}
