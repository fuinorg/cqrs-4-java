package org.fuin.cqrs4j.quarkus.test.app;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fuin.cqrs4j.esc.ProjectionFreshness.Freshness;
import org.fuin.cqrs4j.quarkus.view.QuarkusProjectionFreshnessService;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * REST resource that surfaces read-model freshness to clients: {@code GET /freshness/{view}} returns the current
 * projection position, the lag, and whether the view is caught up. Clients can use it for freshness checks and
 * read-your-writes polling (wait until the view is caught up or its position advances past a known point).
 */
@ThreadSafe
@Path("/freshness")
public class FreshnessResource {

    @Inject
    QuarkusProjectionFreshnessService freshnessService;

    /**
     * Returns the freshness (position, lag, caught-up) of the named view.
     *
     * @param view Name of the view.
     * @return Freshness of the view.
     */
    @GET
    @Path("{view}")
    @Produces(MediaType.APPLICATION_JSON)
    public Freshness freshness(@PathParam("view") final String view) {
        return freshnessService.freshness(view);
    }

}
