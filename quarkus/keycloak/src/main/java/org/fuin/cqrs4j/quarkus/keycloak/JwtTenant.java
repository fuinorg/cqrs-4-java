package org.fuin.cqrs4j.quarkus.keycloak;

import org.fuin.ddd4j.core.Tenant;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.Immutable;

import java.util.Objects;

/**
 * A {@link Tenant} backed by a Keycloak realm. The tenant identifier is derived from the realm name at the end
 * of the issuer URI. Unlike the Spring variant no OIDC discovery or key material is loaded here: quarkus-oidc
 * performs discovery, JWKS retrieval and signature verification natively for the resolved tenant.
 */
@Immutable
public class JwtTenant implements Tenant {

    private final String issuer;

    private final TenantId tenantId;

    /**
     * Constructor with the issuer URI.
     *
     * @param issuerUri Issuer URI like "http://localhost:8082/realms/master".
     */
    public JwtTenant(final String issuerUri) {
        this.issuer = Objects.requireNonNull(issuerUri, "issuerUri==null");
        this.tenantId = new TenantId(KeycloakRealms.realmFromIssuer(issuerUri));
    }

    @Override
    public TenantId getTenantId() {
        return tenantId;
    }

    /**
     * Returns the issuer URI of the tenant's realm.
     *
     * @return Issuer URI.
     */
    public String getIssuer() {
        return issuer;
    }

}
