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

import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Tenant repository backed by Keycloak realms. Tenants are discovered lazily by their issuer URI
 * and cached. A {@link TenantAddedEvent} is published whenever a previously unknown tenant is added.
 * <p>
 * <b>A failed discovery is remembered for a short, growing period.</b> Discovery happens on the request
 * thread the first time an issuer is seen, and it talks to Keycloak. Without a negative cache, a Keycloak
 * that is down or slow is contacted again by <em>every single request</em> carrying that issuer - each one
 * occupying a request thread until the HTTP call gives up. Remembering the failure turns that into one
 * attempt per backoff window; the rest fail immediately, which is what keeps the service responsive for
 * everything that does not need this issuer.
 * <p>
 * Only the <em>discovery</em> is cached this way. A tenant that was resolved once stays cached, so an
 * outage never invalidates issuers that are already known - their tokens keep validating from the keys
 * Nimbus already holds.
 * <p>
 * <b>A known tenant is revalidated once its time-to-live has passed</b>, by {@link #revalidate()}, which a
 * scheduler is expected to call (the starter wires one). A realm that has been deleted or disabled is
 * evicted and announced with a {@link TenantRemovedEvent}; anything that merely <em>failed</em> to answer
 * leaves the tenant in place. Without this a realm removed in Keycloak stays a valid tenant until the
 * application restarts.
 */
@ThreadSafe
public class KeycloakTenantRepository implements JwtTenantRepository {

    /** Delay before a failed issuer discovery is attempted again. */
    static final long INITIAL_RETRY_DELAY_MILLIS = 1_000;

    /** Upper bound for the delay, so a long outage does not stop the tenant from ever being discovered. */
    static final long MAX_RETRY_DELAY_MILLIS = 30_000;

    /** How long a discovered tenant is trusted before {@link #revalidate()} checks it again. */
    static final long DEFAULT_TTL_MILLIS = 300_000;

    /** Bound for the revalidation probe, so a hanging Keycloak cannot block the sweep indefinitely. */
    private static final int PROBE_TIMEOUT_MILLIS = 5_000;

    private static final String OIDC_METADATA_PATH = "/.well-known/openid-configuration";

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakTenantRepository.class);

    private final String baseUri;

    private final Map<String, CachedTenant> tenantMap;

    private final Map<String, FailedDiscovery> failures = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher publisher;

    private final long ttlMillis;

    private final RestTemplate probe;

    /**
     * A discovered tenant and the point in time from which it should be revalidated.
     *
     * @param tenant     Resolved tenant.
     * @param validUntil Epoch milliseconds after which {@link #revalidate()} rechecks it.
     */
    private record CachedTenant(JwtTenant tenant, long validUntil) {
    }

    /**
     * A discovery attempt that failed, and the point in time from which it may be attempted again.
     *
     * @param cause     Failure that was reported, rethrown while the entry is valid.
     * @param retryFrom Epoch milliseconds from which a new attempt is allowed.
     * @param attempt   Number of consecutive failures, used to grow the delay.
     */
    private record FailedDiscovery(RuntimeException cause, long retryFrom, int attempt) {
    }

    /**
     * Constructor with the master realm issuer URI.
     *
     * @param masterIssuerUri Issuer URI like "http://localhost:8082/realms/master"
     * @param publisher       Helper to inform others about new tenants.
     */
    public KeycloakTenantRepository(String masterIssuerUri,
                                    ApplicationEventPublisher publisher) {
        this(masterIssuerUri, publisher, DEFAULT_TTL_MILLIS);
    }

    /**
     * Constructor with an explicit tenant time-to-live.
     *
     * @param masterIssuerUri Issuer URI like "http://localhost:8082/realms/master"
     * @param publisher       Helper to inform others about added and removed tenants.
     * @param ttlMillis       How long a discovered tenant is trusted before it is revalidated.
     */
    public KeycloakTenantRepository(String masterIssuerUri,
                                    ApplicationEventPublisher publisher,
                                    long ttlMillis) {
        super();
        Objects.requireNonNull(masterIssuerUri, "masterIssuerUri==null");
        this.publisher = Objects.requireNonNull(publisher, "applicationEventPublisher==null");
        if (ttlMillis < 1) {
            throw new IllegalArgumentException("ttlMillis must be positive, but was: " + ttlMillis);
        }
        final int p = masterIssuerUri.lastIndexOf('/');
        if (p < 0) {
            throw new IllegalArgumentException("Cannot find realm in: '" + masterIssuerUri + "'");
        }
        this.baseUri = masterIssuerUri.substring(0, p + 1);
        this.tenantMap = new ConcurrentHashMap<>();
        this.ttlMillis = ttlMillis;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(PROBE_TIMEOUT_MILLIS);
        factory.setReadTimeout(PROBE_TIMEOUT_MILLIS);
        this.probe = new RestTemplate(factory);
    }

    @Override
    public Stream<TenantId> getTenantIds() {
        if (tenantMap.isEmpty()) {
            return Stream.empty();
        }
        return tenantMap.values().stream().map(cached -> cached.tenant().getTenantId());
    }

    /**
     * Rechecks every tenant whose time-to-live has passed and drops the ones whose realm is gone.
     * <p>
     * A realm that answers with a client error (deleted or disabled) is evicted and announced with a
     * {@link TenantRemovedEvent}. <b>Anything else keeps the tenant</b> - a timeout, a refused connection
     * or a server error says nothing about whether the realm still exists, and treating it as a removal
     * would log out every tenant whenever Keycloak hiccups. Such a tenant is simply rechecked on the next
     * sweep.
     * <p>
     * Meant to be called from a scheduler; safe to call concurrently.
     */
    public void revalidate() {
        for (final Map.Entry<String, CachedTenant> entry : tenantMap.entrySet()) {
            final CachedTenant cached = entry.getValue();
            if (now() < cached.validUntil()) {
                continue;
            }
            final String issuerUri = entry.getKey();
            try {
                if (realmExists(issuerUri)) {
                    tenantMap.replace(issuerUri, cached, new CachedTenant(cached.tenant(), now() + ttlMillis));
                } else if (tenantMap.remove(issuerUri, cached)) {
                    LOG.info("Tenant realm is gone - removing tenant: {}", issuerUri);
                    publisher.publishEvent(new TenantRemovedEvent(cached.tenant()));
                }
            } catch (final RuntimeException ex) {
                LOG.warn("Could not revalidate tenant '{}' - keeping it and retrying later", issuerUri, ex);
            }
        }
    }

    /**
     * Asks the identity provider whether a realm still exists. Overridable for testing.
     *
     * @param issuerUri Issuer URI of the tenant.
     *
     * @return {@literal true} if the realm answered, {@literal false} if it authoritatively does not exist
     *         (or is disabled).
     *
     * @throws RuntimeException The question could not be answered - the caller must keep the tenant.
     */
    protected boolean realmExists(final String issuerUri) {
        try {
            probe.getForObject(issuerUri + OIDC_METADATA_PATH, String.class);
            return true;
        } catch (final HttpClientErrorException ex) {
            // 4xx is the identity provider answering "no such realm" - the only authoritative negative.
            return false;
        }
    }

    /**
     * Finds the tenant for the given issuer URI. If the tenant is not yet known it is created,
     * cached and announced via a {@link TenantAddedEvent}.
     *
     * @param issuerUri Issuer URI of the tenant. Must start with the master realm base URI.
     * @return Matching tenant.
     */
    @Override
    public Optional<JwtTenant> findByIssuer(String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuer==null");
        final CachedTenant known = tenantMap.get(issuerUri);
        if (known != null) {
            return Optional.of(known.tenant());
        }
        if (!issuerUri.startsWith(baseUri)) {
            throw new IllegalArgumentException("Issuer URI '" + issuerUri + "' does not start with '" + baseUri + "'");
        }
        final FailedDiscovery previous = failures.get(issuerUri);
        if (previous != null && now() < previous.retryFrom()) {
            // Fail immediately instead of occupying this request thread with a call that just failed.
            throw previous.cause();
        }
        try {
            final JwtTenant tenant = createTenant(issuerUri);
            failures.remove(issuerUri);
            tenantMap.put(issuerUri, new CachedTenant(tenant, now() + ttlMillis));
            publisher.publishEvent(new TenantAddedEvent(tenant));
            return Optional.of(tenant);
        } catch (final RuntimeException ex) {
            failures.put(issuerUri, nextFailure(previous, ex));
            throw ex;
        }
    }

    /**
     * Builds the negative cache entry for a failed discovery, doubling the delay for each consecutive
     * failure up to {@link #MAX_RETRY_DELAY_MILLIS}.
     *
     * @param previous Previous failure for the same issuer, or {@literal null} if this is the first.
     * @param cause    Failure that was reported.
     * @return Entry to remember.
     */
    private FailedDiscovery nextFailure(@Nullable final FailedDiscovery previous,
                                        final RuntimeException cause) {
        final int attempt = previous == null ? 1 : previous.attempt() + 1;
        long delay = INITIAL_RETRY_DELAY_MILLIS;
        for (int i = 1; i < attempt && delay < MAX_RETRY_DELAY_MILLIS; i++) {
            delay = delay * 2;
        }
        return new FailedDiscovery(cause, now() + Math.min(delay, MAX_RETRY_DELAY_MILLIS), attempt);
    }

    /**
     * Performs the OIDC discovery for an issuer. Overridable for testing.
     *
     * @param issuerUri Issuer URI of the tenant.
     * @return Newly discovered tenant.
     */
    protected JwtTenant createTenant(final String issuerUri) {
        return new JwtTenant(issuerUri);
    }

    /**
     * Returns the current time in epoch milliseconds. Overridable for testing.
     *
     * @return Current time.
     */
    protected long now() {
        return System.currentTimeMillis();
    }

}
