package org.fuin.cqrs4j.quarkus.keycloak;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * Populates the {@link WritableTenantContext} from the realm of the authenticated request's OIDC token and
 * clears it again when the response is sent. Mirrors the Spring pair of the {@code jwtDecoder} (which pushes the
 * realm into the tenant context on every decode) and the {@code CleanupTenantContextFilter} (which clears it
 * after the request). Requests without a bearer token leave the tenant context untouched.
 */
@ThreadSafe
@Provider
@ApplicationScoped
public class TenantContextRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    WritableTenantContext tenantContext;

    @Inject
    JwtTenantRepository tenantRepository;

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        final String issuer = jwt == null ? null : jwt.getIssuer();
        if (issuer == null) {
            return;
        }
        tenantRepository.findByIssuer(issuer);
        tenantContext.setTenantId(new TenantId(KeycloakRealms.realmFromIssuer(issuer)));
    }

    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) {
        tenantContext.clear();
    }

}
