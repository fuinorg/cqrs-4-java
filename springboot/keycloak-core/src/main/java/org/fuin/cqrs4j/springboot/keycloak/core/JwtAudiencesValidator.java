/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.springboot.keycloak.core;

import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validates that the "aud" claim of a {@link Jwt} contains at least one of the expected audiences.
 * <p>
 * Without this check every token issued for <b>any</b> client of an accepted realm is valid for the
 * resource server: Keycloak puts {@code account} into {@code aud} by default, so a token minted for an
 * unrelated client of the same realm passes signature, issuer and expiry validation unchallenged. The
 * Keycloak client therefore needs an audience mapper emitting the value configured here.
 * <p>
 * A token without an {@code aud} claim is rejected - an audience that cannot be checked is not an
 * audience that matches.
 * <p>
 * Spring Security ships {@code org.springframework.security.oauth2.jwt.JwtAudienceValidator}, but it
 * accepts a <b>single</b> audience, and {@code DelegatingOAuth2TokenValidator} combines validators with
 * AND - so several of them cannot express "one of these". Hence this class. The matching semantics are
 * the same as those of Spring Boot's own {@code spring.security.oauth2.resourceserver.jwt.audiences}
 * property.
 */
@ThreadSafe
public final class JwtAudiencesValidator implements OAuth2TokenValidator<Jwt> {

    private final Set<String> expectedAudiences;

    private final JwtClaimValidator<List<String>> validator;

    /**
     * Constructor with the accepted audiences.
     *
     * @param expectedAudiences Audiences of which a token must carry at least one. Must not be empty - a
     *                          validator that accepts everything only looks like protection.
     */
    public JwtAudiencesValidator(final Collection<String> expectedAudiences) {
        Objects.requireNonNull(expectedAudiences, "expectedAudiences==null");
        if (expectedAudiences.isEmpty()) {
            throw new IllegalArgumentException("expectedAudiences is empty");
        }
        this.expectedAudiences = Set.copyOf(expectedAudiences);
        this.validator = new JwtClaimValidator<>(JwtClaimNames.AUD,
                aud -> aud != null && !Collections.disjoint(aud, this.expectedAudiences));
    }

    @Override
    public OAuth2TokenValidatorResult validate(final Jwt token) {
        Objects.requireNonNull(token, "token==null");
        return validator.validate(token);
    }

    /**
     * Returns the audiences a token must carry at least one of.
     *
     * @return Immutable set of accepted audiences, never empty.
     */
    public Set<String> getExpectedAudiences() {
        return expectedAudiences;
    }

}
