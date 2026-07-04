package org.fuin.cqrs4j.quarkus.keycloak;

import org.fuin.cqrs4j.core.TenantRepository;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Optional;

/**
 * A {@link TenantRepository} that can additionally resolve tenants by their JWT issuer URI. Implementations are
 * expected to be thread safe.
 */
@ThreadSafe
public interface JwtTenantRepository extends TenantRepository {

    /**
     * Finds the tenant for the given issuer URI. If the tenant is not yet known it is created, cached and
     * announced via a {@code TenantAddedEvent}.
     *
     * @param issuerUri Issuer URI of the tenant. Must start with the master realm base URI.
     * @return Matching tenant.
     */
    Optional<JwtTenant> findByIssuer(String issuerUri);

}
