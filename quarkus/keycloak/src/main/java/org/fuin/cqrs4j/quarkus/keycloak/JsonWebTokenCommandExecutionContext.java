package org.fuin.cqrs4j.quarkus.keycloak;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.ddd4j.core.SimpleRole;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.User;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.List;

/**
 * {@link CommandExecutionContext} that derives the tenant and user from the OIDC bearer token of the current
 * request. Mirrors the Spring {@code KeycloakTokenWrapper}: the user id is the token subject, the user name is
 * the {@code preferred_username} claim, and the tenant is the Keycloak realm taken from the {@code iss} claim.
 */
@ThreadSafe
@RequestScoped
public class JsonWebTokenCommandExecutionContext implements CommandExecutionContext {

    @Inject
    JsonWebToken jwt;

    @Override
    public TenantId getTenantId() {
        return new TenantId(getRealm());
    }

    @Override
    public User getUser() {
        final String userId = getUserId();
        final String userName = getPreferredUsername();
        return new User() {
            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public String getUserName() {
                return userName;
            }
        };
    }

    /**
     * Returns the unique user identifier (the token's subject).
     *
     * @return Subject ("sub") claim.
     */
    public String getUserId() {
        return jwt.getSubject();
    }

    /**
     * Returns the preferred user name.
     *
     * @return "preferred_username" claim.
     */
    public String getPreferredUsername() {
        return stringClaim("preferred_username");
    }

    /**
     * Returns the Keycloak realm derived from the issuer claim.
     *
     * @return Realm name.
     */
    public String getRealm() {
        return KeycloakRealms.realmFromIssuer(getIssuer());
    }

    /**
     * Returns the issuer claim.
     *
     * @return "iss" claim.
     */
    public String getIssuer() {
        return stringClaim("iss");
    }

    /**
     * Returns the realm roles ("realm_access.roles") of the user.
     *
     * @return List of roles, never {@code null}.
     */
    public List<SimpleRole> getUserRoles() {
        final Object claim = jwt.getClaim("realm_access");
        if (!(claim instanceof JsonObject realmAccess)) {
            return List.of();
        }
        final JsonArray roles = realmAccess.getJsonArray("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(value -> value.getValueType() == JsonValue.ValueType.STRING)
                .map(value -> ((JsonString) value).getString())
                .map(SimpleRole::new)
                .toList();
    }

    private String stringClaim(final String name) {
        final Object value = jwt.getClaim(name);
        if (value instanceof String v) {
            return v;
        }
        throw new IllegalArgumentException("Expected claim '" + name + "' to be a String, but was: "
                + (value == null ? "null" : value.getClass().getName()));
    }

}
