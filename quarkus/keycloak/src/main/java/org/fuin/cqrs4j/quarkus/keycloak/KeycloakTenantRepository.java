package org.fuin.cqrs4j.quarkus.keycloak;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Tenant repository backed by Keycloak realms. Tenants are discovered lazily by their issuer URI and cached. A
 * {@link TenantAddedEvent} is fired via CDI whenever a previously unknown tenant is added, so the query side can
 * provision a projection / datasource for the new tenant.
 */
@ThreadSafe
@ApplicationScoped
public class KeycloakTenantRepository implements JwtTenantRepository {

    /** Configuration key for the master realm issuer URI. */
    public static final String KEY_MASTER_ISSUER_URI = "org.fuin.cqrs4j.keycloak.master-issuer-uri";

    private final String baseUri;

    private final Map<String, JwtTenant> tenantMap;

    private final Event<TenantAddedEvent> tenantAddedEvent;

    /**
     * Constructor with the master realm issuer URI and the CDI event used to announce new tenants.
     *
     * @param masterIssuerUri  Issuer URI of the master realm like "http://localhost:8082/realms/master".
     * @param tenantAddedEvent Event fired whenever a new tenant is discovered.
     */
    @Inject
    public KeycloakTenantRepository(@ConfigProperty(name = KEY_MASTER_ISSUER_URI) final String masterIssuerUri,
                                    final Event<TenantAddedEvent> tenantAddedEvent) {
        this.baseUri = KeycloakRealms.baseUri(Objects.requireNonNull(masterIssuerUri, "masterIssuerUri==null"));
        this.tenantAddedEvent = Objects.requireNonNull(tenantAddedEvent, "tenantAddedEvent==null");
        this.tenantMap = new ConcurrentHashMap<>();
    }

    @Override
    public Stream<TenantId> getTenantIds() {
        if (tenantMap.isEmpty()) {
            return Stream.empty();
        }
        return tenantMap.values().stream().map(JwtTenant::getTenantId);
    }

    @Override
    public Optional<JwtTenant> findByIssuer(final String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuerUri==null");
        final JwtTenant cached = tenantMap.get(issuerUri);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (!issuerUri.startsWith(baseUri)) {
            throw new IllegalArgumentException(
                    "Issuer URI '" + issuerUri + "' does not start with '" + baseUri + "'");
        }
        final JwtTenant tenant = new JwtTenant(issuerUri);
        tenantMap.put(issuerUri, tenant);
        tenantAddedEvent.fire(new TenantAddedEvent(tenant));
        return Optional.of(tenant);
    }

}
