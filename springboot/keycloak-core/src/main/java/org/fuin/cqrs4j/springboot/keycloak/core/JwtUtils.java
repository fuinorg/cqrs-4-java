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
/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fuin.cqrs4j.springboot.keycloak.core;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

/**
 * Allows resolving configuration from an <a href=
 * "https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig">OpenID
 * Provider Configuration</a> or
 * <a href="https://tools.ietf.org/html/rfc8414#section-3.1">Authorization Server Metadata
 * Request</a> based on provided issuer and method invoked.
 *
 * Copy of <a href="https://github.com/spring-projects/spring-security/blob/main/oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt/JwtDecoderProviderConfigurationUtils.java">JwtDecoderProviderConfigurationUtils.java</a>.
 *
 * @author Thomas Vitale
 * @author Rafiullah Hamedy
 * @since 5.2
 */
@ThreadSafe
final class JwtUtils {

    private static final String OIDC_METADATA_PATH = "/.well-known/openid-configuration";

    private static final String OAUTH_METADATA_PATH = "/.well-known/oauth-authorization-server";

    private static final RestTemplate rest = new RestTemplate();

    static {
        int connectTimeout = Integer.parseInt(System.getProperty("sun.net.client.defaultConnectTimeout", "30000"));
        int readTimeout = Integer.parseInt(System.getProperty("sun.net.client.defaultReadTimeout", "30000"));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        rest.setRequestFactory(requestFactory);
    }

    private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP = new ParameterizedTypeReference<>() {
    };

    private JwtUtils() {
    }

    static Map<String, Object> getConfigurationForOidcIssuerLocation(String oidcIssuerLocation) {
        return getConfiguration(oidcIssuerLocation, rest, oidc(URI.create(oidcIssuerLocation)));
    }

    static Map<String, Object> getConfigurationForIssuerLocation(String issuer, RestOperations rest) {
        URI uri = URI.create(issuer);
        return getConfiguration(issuer, rest, oidc(uri), oidcRfc8414(uri), oauth(uri));
    }

    static Map<String, Object> getConfigurationForIssuerLocation(String issuer) {
        return getConfigurationForIssuerLocation(issuer, rest);
    }

    static void validateIssuer(Map<String, Object> configuration, String issuer) {
        String metadataIssuer = getMetadataIssuer(configuration);
        Assert.state(issuer.equals(metadataIssuer), () -> "The Issuer \"" + metadataIssuer
                + "\" provided in the configuration did not " + "match the requested issuer \"" + issuer + "\"");
    }

    static <C extends SecurityContext> void addJWSAlgorithms(ConfigurableJWTProcessor<C> jwtProcessor) {
        JWSKeySelector<C> selector = jwtProcessor.getJWSKeySelector();
        if (selector instanceof JWSVerificationKeySelector) {
            JWKSource<C> jwkSource = ((JWSVerificationKeySelector<C>) selector).getJWKSource();
            Set<JWSAlgorithm> algorithms = getJWSAlgorithms(jwkSource);
            selector = new JWSVerificationKeySelector<>(algorithms, jwkSource);
            jwtProcessor.setJWSKeySelector(selector);
        }
    }

    static <C extends SecurityContext> Set<JWSAlgorithm> getJWSAlgorithms(JWKSource<C> jwkSource) {
        JWKMatcher jwkMatcher = new JWKMatcher.Builder().publicOnly(true)
                .keyUses(KeyUse.SIGNATURE, null)
                .keyTypes(KeyType.RSA, KeyType.EC)
                .build();
        Set<JWSAlgorithm> jwsAlgorithms = new HashSet<>();
        try {
            List<? extends JWK> jwks = jwkSource.get(new JWKSelector(jwkMatcher), null);
            for (JWK jwk : jwks) {
                if (jwk.getAlgorithm() != null) {
                    JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwk.getAlgorithm().getName());
                    jwsAlgorithms.add(jwsAlgorithm);
                }
                else {
                    if (jwk.getKeyType() == KeyType.RSA) {
                        jwsAlgorithms.addAll(JWSAlgorithm.Family.RSA);
                    }
                    else if (jwk.getKeyType() == KeyType.EC) {
                        jwsAlgorithms.addAll(JWSAlgorithm.Family.EC);
                    }
                }
            }
        }
        catch (KeySourceException ex) {
            throw new IllegalStateException(ex);
        }
        Assert.notEmpty(jwsAlgorithms, "Failed to find any algorithms from the JWK set");
        return jwsAlgorithms;
    }

    static Set<SignatureAlgorithm> getSignatureAlgorithms(JWKSource<SecurityContext> jwkSource) {
        Set<JWSAlgorithm> jwsAlgorithms = getJWSAlgorithms(jwkSource);
        Set<SignatureAlgorithm> signatureAlgorithms = new HashSet<>();
        for (JWSAlgorithm jwsAlgorithm : jwsAlgorithms) {
            SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.from(jwsAlgorithm.getName());
            if (signatureAlgorithm != null) {
                signatureAlgorithms.add(signatureAlgorithm);
            }
        }
        return signatureAlgorithms;
    }

    private static String getMetadataIssuer(Map<String, Object> configuration) {
        if (configuration.containsKey("issuer")) {
            return configuration.get("issuer").toString();
        }
        return "(unavailable)";
    }

    private static Map<String, Object> getConfiguration(String issuer, RestOperations rest, URI... uris) {
        String errorMessage = "Unable to resolve the Configuration with the provided Issuer of " + "\"" + issuer + "\"";
        for (URI uri : uris) {
            try {
                RequestEntity<Void> request = RequestEntity.get(uri).build();
                ResponseEntity<Map<String, Object>> response = rest.exchange(request, STRING_OBJECT_MAP);
                Map<String, Object> configuration = response.getBody();
                if (configuration == null) {
                    throw new IllegalArgumentException("The Configuration response body must not be null");
                }
                Assert.isTrue(configuration.get("jwks_uri") != null, "The public JWK set URI must not be null");
                return configuration;
            }
            catch (IllegalArgumentException ex) {
                throw ex;
            }
            catch (RuntimeException ex) {
                if (!(ex instanceof HttpClientErrorException
                        && ((HttpClientErrorException) ex).getStatusCode().is4xxClientError())) {
                    throw new IllegalArgumentException(errorMessage, ex);
                }
                // else try another endpoint
            }
        }
        throw new IllegalArgumentException(errorMessage);
    }

    private static URI oidc(URI issuer) {
        // @formatter:off
        return UriComponentsBuilder.fromUri(issuer)
                .replacePath(issuer.getPath() + OIDC_METADATA_PATH)
                .build(Collections.emptyMap());
        // @formatter:on
    }

    private static URI oidcRfc8414(URI issuer) {
        // @formatter:off
        return UriComponentsBuilder.fromUri(issuer)
                .replacePath(OIDC_METADATA_PATH + issuer.getPath())
                .build(Collections.emptyMap());
        // @formatter:on
    }

    private static URI oauth(URI issuer) {
        // @formatter:off
        return UriComponentsBuilder.fromUri(issuer)
                .replacePath(OAUTH_METADATA_PATH + issuer.getPath())
                .build(Collections.emptyMap());
        // @formatter:on
    }

}
