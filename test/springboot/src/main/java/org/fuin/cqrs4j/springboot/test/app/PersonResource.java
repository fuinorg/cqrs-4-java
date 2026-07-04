package org.fuin.cqrs4j.springboot.test.app;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.fuin.cqrs4j.springboot.query.core.view.ProjectionFreshnessService;
import org.fuin.cqrs4j.springboot.test.model.PersonEntity;
import org.fuin.cqrs4j.springboot.test.model.PersonId;
import org.fuin.cqrs4j.springboot.test.view.PersonsView;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST resource providing the persons.
 */
@ThreadSafe
@RestController
@Transactional
@RequestMapping(value = "/persons",
        consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
)
public class PersonResource {

    private static final Logger LOG = LoggerFactory.getLogger(PersonResource.class);

    /** Response header advertising the projection position the read model is current as of. */
    public static final String PROJECTION_POSITION_HEADER = "X-Projection-Position";

    @PersistenceContext
    EntityManager em;

    private final ProjectionFreshnessService freshnessService;

    /**
     * Constructor with mandatory data.
     *
     * @param freshnessService Provides the current projection position for the freshness header.
     */
    public PersonResource(final ProjectionFreshnessService freshnessService) {
        this.freshnessService = Objects.requireNonNull(freshnessService, "freshnessService==null");
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonEntity> read(@PathVariable("id") PersonId id) {
        LOG.info("read({}) / em={}", id, em);
        final PersonEntity person = em.find(PersonEntity.class, id.asBaseType());
        if (person == null) {
            LOG.info("result: NOT_FOUND");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        LOG.info("result: {}", person);
        // Advertise how fresh this read model is: the projection position it has consumed up to.
        return ResponseEntity.ok()
                .header(PROJECTION_POSITION_HEADER, String.valueOf(freshnessService.position(PersonsView.NAME)))
                .body(person);
    }

}
