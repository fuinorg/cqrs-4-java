package org.fuin.cqrs4j.springboot.test.app;

import org.fuin.cqrs4j.esc.ProjectionFreshness.Freshness;
import org.fuin.cqrs4j.springboot.query.core.view.ProjectionFreshnessService;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST resource that surfaces read-model freshness to clients: {@code GET /freshness/{view}} returns the current
 * projection position, the lag, and whether the view is caught up. Clients can use it for freshness checks and
 * read-your-writes polling (wait until the view is caught up or its position advances past a known point).
 */
@ThreadSafe
@RestController
@RequestMapping(value = "/freshness", produces = MediaType.APPLICATION_JSON_VALUE)
public class FreshnessResource {

    private final ProjectionFreshnessService freshnessService;

    /**
     * Constructor with mandatory data.
     *
     * @param freshnessService Provides the freshness of a view.
     */
    public FreshnessResource(final ProjectionFreshnessService freshnessService) {
        this.freshnessService = Objects.requireNonNull(freshnessService, "freshnessService==null");
    }

    /**
     * Returns the freshness (position, lag, caught-up) of the named view.
     *
     * @param view Name of the view.
     * @return Freshness of the view.
     */
    @GetMapping("/{view}")
    public Freshness freshness(@PathVariable("view") final String view) {
        return freshnessService.freshness(view);
    }

}
