package org.fuin.cqrs4j.springboot.test;

import jakarta.json.bind.Jsonb;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.fuin.cqrs4j.test.model.PersonEntity;
import org.fuin.cqrs4j.test.model.PersonId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST resource providing the persons.
 */
@RestController
@Transactional
@RequestMapping(value = "/persons",
        consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
)
public class PersonResource {

    private static final Logger LOG = LoggerFactory.getLogger(PersonResource.class);

    @PersistenceContext
    EntityManager em;

    @Autowired
    Jsonb jsonb;

    @GetMapping("/{id}")
    public ResponseEntity<String> read(@PathVariable("id") PersonId id) {
        final PersonEntity person = em.find(PersonEntity.class, id.asBaseType());
        if (person == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        // TODO Return PersonEntity - Currently somehow Jackson kicks in
        // and does not know how to serialize the custom types  like PersonId and PersonName
        return new ResponseEntity<>(jsonb.toJson(person), HttpStatus.OK);
    }

}
