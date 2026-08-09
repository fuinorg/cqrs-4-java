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

package org.fuin.cqrs4j.test.helper;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The structural half of "no permit-all chain ever ships", for any Spring Boot application.
 *
 * <h2>Why an application needs this at all</h2>
 * <p>
 * Every application that can be run locally grows a permit-all filter chain sooner or later - it is how
 * a developer starts the thing without a Keycloak, and {@code cqrs-4-java-springboot-security} is built
 * around that: its chain is {@code @ConditionalOnMissingBean}, so a permit-all one replaces it whole.
 * That escape hatch is fine exactly as long as it cannot be switched on in production, which is two
 * separate facts: the chain is gated on a profile, <b>and</b> the packaged configuration does not
 * activate that profile. Both are asserted here.
 *
 * <h2>Usage</h2>
 * <p>
 * Once per packaged deployable. The test class cannot be shared, because ArchUnit scans the classpath of
 * the module it runs in:
 *
 * <pre>
 * &#64;AnalyzeClasses(packages = "com.example", importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest {
 *
 *     &#64;ArchTest
 *     static final ArchRule permitAll = SecurityArchRules.PERMIT_ALL_IS_GATED_ON_THE_LOCAL_PROFILE;
 *
 *     &#64;Test
 *     void testTheLocalProfileIsNotActiveByDefault() throws Exception {
 *         SecurityArchRules.assertLocalProfileNotActive();
 *     }
 * }
 * </pre>
 *
 * <h2>What it does not catch</h2>
 * <p>
 * Two classes could split the calls between them and evade the predicate, and a rule read out of
 * configuration is invisible to it. The actual guarantee is behavioural - a test that watches an
 * unauthenticated request being refused - and this is the cheap structural backstop that fails at build
 * time rather than at container start. It is also why
 * {@code Cqrs4jSecurityProperties} has no way to express a permit: what lives in YAML cannot be seen
 * from here.
 */
public final class SecurityArchRules {

    /** Simple name of the annotation that gates the escape hatch. */
    private static final String PROFILE_ANNOTATION = "Profile";

    /** Profile the escape hatch is allowed under. */
    private static final String LOCAL = "local";

    /** The one file Spring Boot always loads, and therefore the only one worth asserting on. */
    private static final String APPLICATION_YML = "/application.yml";

    /**
     * Matches a class that opens <b>every</b> request: it calls {@code anyRequest()} and
     * {@code permitAll()} and never {@code authenticated()}.
     * <p>
     * The third condition is what makes this usable in an application that has real authentication. A
     * production chain legitimately calls {@code permitAll()} - for the actuator - and
     * {@code anyRequest()} on its way to {@code authenticated()}. These are three unordered,
     * class-scoped existence checks and not an analysis of the fluent chain, so without the third one a
     * real chain matches and the rule demands a {@code @Profile("local")} on a production class.
     * <p>
     * That is not hypothetical: the two-clause version of this predicate was copied into two projects,
     * fixed in one of them, and left in the other.
     * <p>
     * Matched by method <em>name</em> rather than declaring type on purpose. {@code anyRequest()} is
     * inherited from {@code AbstractRequestMatcherRegistry} and the deprecated {@code authorizeRequests}
     * API uses different classes again, so a type-based match would quietly stop matching after a Spring
     * Security refactor - the worst outcome for a rule like this, because it keeps passing.
     */
    public static final DescribedPredicate<JavaClass> OPEN_EVERY_REQUEST =
            new DescribedPredicate<JavaClass>("open every request") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    return callsSpringSecurityMethod(javaClass, "anyRequest")
                            && callsSpringSecurityMethod(javaClass, "permitAll")
                            && !callsSpringSecurityMethod(javaClass, "authenticated");
                }
            };

    /**
     * Requires a permit-all chain to be gated on {@code @Profile("local")}.
     */
    public static final ArchCondition<JavaClass> BE_GATED_ON_THE_LOCAL_PROFILE =
            new ArchCondition<JavaClass>("be gated on @Profile(\"local\")") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                    if (!gatedOnLocalProfile(item)) {
                        events.add(SimpleConditionEvent.violated(item, item.getFullName()
                                + " permits every request but is not gated on @Profile(\"local\"). A "
                                + "permit-all filter chain may exist only under the 'local' profile or in "
                                + "test sources, never in a packaged production configuration."));
                    }
                }
            };

    /**
     * The escape hatch may exist - it is how the application runs on a developer machine - but only
     * where it cannot be switched on in production.
     */
    public static final ArchRule PERMIT_ALL_IS_GATED_ON_THE_LOCAL_PROFILE = classes()
            .that(OPEN_EVERY_REQUEST)
            .should(BE_GATED_ON_THE_LOCAL_PROFILE)
            .allowEmptyShould(true);

    private SecurityArchRules() {
        throw new UnsupportedOperationException("Cannot instantiate utility class");
    }

    /**
     * Fails if the packaged {@code application.yml} activates the {@code local} profile.
     * <p>
     * The other half of the guarantee. Gating the chain on a profile is worth nothing if the packaged
     * configuration turns that profile on, which is the realistic way this actually ships enabled:
     * somebody adds {@code spring.profiles.active: local} to make something work locally and it travels.
     * <p>
     * Reads only {@code application.yml}. {@code application-local.yml} is <em>supposed</em> to hold
     * local settings, so asserting on it would be wrong.
     *
     * @throws Exception The file could not be read.
     */
    public static void assertLocalProfileNotActive() throws Exception {
        assertLocalProfileNotActive(APPLICATION_YML);
    }

    /**
     * Same, for a configuration file under another name.
     *
     * @param classpathResource Absolute class-path location, for example {@code /application.yml}.
     *
     * @throws Exception The file could not be read.
     */
    public static void assertLocalProfileNotActive(final String classpathResource) throws Exception {
        try (InputStream in = SecurityArchRules.class.getResourceAsStream(classpathResource)) {
            assertThat(in).describedAs(classpathResource + " on the class path").isNotNull();
            final Map<String, Object> yaml = new Yaml().load(in);
            assertThat(activeProfiles(yaml))
                    .describedAs(classpathResource + " must not activate the 'local' profile - it "
                            + "replaces the security chain with a permit-all one")
                    .doesNotContain(LOCAL);
        }
    }

    private static boolean callsSpringSecurityMethod(final JavaClass javaClass, final String methodName) {
        return javaClass.getMethodCallsFromSelf().stream()
                .anyMatch(call -> methodName.equals(call.getTarget().getName())
                        && call.getTargetOwner().getPackageName().startsWith("org.springframework.security"));
    }

    /**
     * Accepts the gate on the class itself or on any enclosing class - Spring processes a nested
     * {@code @Configuration} only when its enclosing class is processed.
     * <p>
     * The annotation is matched by simple name rather than by type so that this module needs no
     * dependency on {@code spring-context}, the same approach units4j takes for the thread-safety
     * annotations.
     */
    private static boolean gatedOnLocalProfile(final JavaClass javaClass) {
        Optional<JavaClass> current = Optional.of(javaClass);
        while (current.isPresent()) {
            if (hasLocalProfileAnnotation(current.get())) {
                return true;
            }
            current = current.get().getEnclosingClass();
        }
        return false;
    }

    private static boolean hasLocalProfileAnnotation(final JavaClass javaClass) {
        return javaClass.getAnnotations().stream()
                .filter(annotation -> PROFILE_ANNOTATION.equals(annotation.getRawType().getSimpleName()))
                .anyMatch(annotation -> annotation.get("value")
                        .map(SecurityArchRules::containsLocal)
                        .orElse(false));
    }

    private static boolean containsLocal(final Object value) {
        if (value instanceof Object[] values) {
            return Arrays.stream(values).anyMatch(entry -> LOCAL.equals(String.valueOf(entry)));
        }
        return LOCAL.equals(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> activeProfiles(final Map<String, Object> yaml) {
        final List<String> result = new ArrayList<>();
        if (yaml == null) {
            return result;
        }
        final Object spring = yaml.get("spring");
        if (!(spring instanceof Map)) {
            return result;
        }
        final Object profiles = ((Map<?, ?>) spring).get("profiles");
        if (!(profiles instanceof Map)) {
            return result;
        }
        for (final String key : List.of("active", "include", "default")) {
            final Object value = ((Map<?, ?>) profiles).get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String) {
                result.addAll(Arrays.asList(((String) value).split(",")));
            } else if (value instanceof Collection) {
                ((Collection<Object>) value).forEach(entry -> result.add(String.valueOf(entry)));
            } else {
                result.add(String.valueOf(value));
            }
        }
        final List<String> trimmed = new ArrayList<>();
        result.forEach(entry -> trimmed.add(entry.trim()));
        return trimmed;
    }

}
