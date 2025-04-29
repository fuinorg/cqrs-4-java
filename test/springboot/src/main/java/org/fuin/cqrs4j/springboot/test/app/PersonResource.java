package org.fuin.cqrs4j.springboot.test.app;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.fuin.cqrs4j.springboot.test.model.PersonEntity;
import org.fuin.cqrs4j.springboot.test.model.PersonId;
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

    @PersistenceContext
    EntityManager em;

    @GetMapping("/{id}")
    public ResponseEntity<PersonEntity> read(@PathVariable("id") PersonId id) {
        final PersonEntity person = em.find(PersonEntity.class, id.asBaseType());
        if (person == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(person, HttpStatus.OK);
    }

}
