package org.fuin.cqrs4j.springboot.test.freshness;

import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ProjectionService;
import org.fuin.cqrs4j.esc.ProjectionStreamIds;
import org.fuin.cqrs4j.springboot.query.core.view.ProjectionFreshnessService;
import org.fuin.cqrs4j.springboot.query.core.view.QryProjectionService;
import org.fuin.cqrs4j.springboot.test.app.FreshnessResource;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionStreamId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker-free end-to-end test for the read-model freshness endpoint against an embedded HSQLDB. It seeds a
 * projection checkpoint and asserts {@code GET /freshness/{view}} reports it (with lag 0 / caught-up, since the
 * stub event store has no projection stream). Exercises the real {@link QryProjectionService} +
 * {@link ProjectionFreshnessService} + {@link FreshnessResource}.
 */
@SpringBootTest(classes = ProjectionFreshnessSliceTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:hsqldb:mem:freshness-test",
                "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureMockMvc
class ProjectionFreshnessSliceTest {

    private static final String VIEW = "Persons";

    private static final ViewRegistry.Entry ENTRY = new ViewRegistry.Entry(View.class, VIEW, "PersonsView",
            "PersonsProjection", "PersonsProjection", "* * * * * *", 100,
            Set.of(new EventType("PersonCreatedEvent")));

    private static final ProjectionStreamId STREAM_ID = ProjectionStreamIds.of(ENTRY);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectionService projectionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void freshnessEndpointReportsSeededPosition() throws Exception {
        // PREPARE: seed a projection checkpoint at position 42
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                projectionService.updateProjectionPosition(STREAM_ID, 42L));

        // TEST & VERIFY
        mockMvc.perform(get("/freshness/{view}", VIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(42))
                .andExpect(jsonPath("$.lag").value(0))
                .andExpect(jsonPath("$.caughtUp").value(true));
    }

    /**
     * Minimal slice: embedded HSQLDB + the real checkpoint repository, freshness service and endpoint, with a
     * stub event store (no projection stream ⇒ lag 0) and a one-view registry.
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class
    })
    @EntityScan("org.fuin.cqrs4j.jpa.query")
    static class TestApp {

        @Bean
        ProjectionService projectionService() {
            return new QryProjectionService();
        }

        @Bean
        ViewRegistry viewRegistry() {
            final ViewRegistry registry = mock(ViewRegistry.class);
            when(registry.getViews()).thenReturn(List.of(ENTRY));
            return registry;
        }

        @Bean
        EventStore eventStore() {
            // Default mock: streamExists(..) returns false ⇒ ProjectionLag reports 0 ⇒ caught up.
            return mock(EventStore.class);
        }

        @Bean
        ProjectionFreshnessService projectionFreshnessService(final ViewRegistry viewRegistry,
                                                              final EventStore eventStore,
                                                              final ProjectionService projectionService) {
            return new ProjectionFreshnessService(viewRegistry, eventStore, projectionService);
        }

        @Bean
        FreshnessResource freshnessResource(final ProjectionFreshnessService freshnessService) {
            return new FreshnessResource(freshnessService);
        }

    }

}
