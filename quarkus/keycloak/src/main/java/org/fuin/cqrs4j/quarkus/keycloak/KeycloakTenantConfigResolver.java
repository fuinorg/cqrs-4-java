package org.fuin.cqrs4j.quarkus.keycloak;

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Resolves the quarkus-oidc tenant configuration dynamically from the issuer of the incoming bearer token. This
 * is the Quarkus-native replacement for the Spring multi-tenant key selection / issuer validation: the realm is
 * taken from the (unverified) token issuer, the tenant is looked up (and lazily discovered) via the
 * {@link JwtTenantRepository}, and a per-realm {@link OidcTenantConfig} pointing at that realm's issuer is
 * returned so quarkus-oidc can perform discovery, JWKS retrieval and issuer validation for it. Requests without
 * a recognizable bearer token fall back to the default tenant configuration.
 */
@ThreadSafe
@ApplicationScoped
public class KeycloakTenantConfigResolver implements TenantConfigResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    JwtTenantRepository tenantRepository;

    @Override
    public Uni<OidcTenantConfig> resolve(final RoutingContext routingContext,
                                         final OidcRequestContext<OidcTenantConfig> requestContext) {
        return Uni.createFrom().item(resolveConfig(routingContext.request().getHeader("Authorization")));
    }

    /**
     * Resolves the per-tenant OIDC configuration from an {@code Authorization} header value, or {@code null} to
     * fall back to the default tenant configuration.
     *
     * @param authorization Value of the {@code Authorization} request header (may be {@code null}).
     * @return Per-tenant configuration, or {@code null} for the default tenant.
     */
    @Nullable
    OidcTenantConfig resolveConfig(final @Nullable String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        final Optional<String> issuer = KeycloakRealms.issuerFromBearerToken(authorization.substring(BEARER_PREFIX.length()));
        if (issuer.isEmpty()) {
            return null;
        }
        final String issuerUri = issuer.get();
        try {
            tenantRepository.findByIssuer(issuerUri);
        } catch (final IllegalArgumentException ex) {
            // Unknown issuer (outside the master realm base URI) -> fall back to the default tenant, which will
            // reject the token during validation.
            return null;
        }
        return OidcTenantConfig.authServerUrl(issuerUri)
                .tenantId(KeycloakRealms.realmFromIssuer(issuerUri))
                .build();
    }

}
