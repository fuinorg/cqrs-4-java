# cqrs-4-java

# Command Query Responsibility Segregation for Java
Base classes for Command Query Responsibility Segregation (CQRS) with Java

[![Java Maven Build](https://github.com/fuinorg/cqrs-4-java/actions/workflows/maven.yml/badge.svg)](https://github.com/fuinorg/cqrs-4-java/actions/workflows/maven.yml)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.fuin.cqrs4j%3Acqrs-4-java&metric=coverage)](https://sonarcloud.io/dashboard?id=org.fuin.cqrs4j%3Acqrs-4-java)
[![Maven Central](https://img.shields.io/maven-central/v/org.fuin/cqrs-4-java.svg)](https://central.sonatype.com/artifact/org.fuin/cqrs-4-java)
[![LGPLv3 License](http://img.shields.io/badge/license-LGPLv3-blue.svg)](https://www.gnu.org/licenses/lgpl.html)
[![Java Development Kit 17](https://img.shields.io/badge/JDK-17-green.svg)](https://openjdk.java.net/projects/jdk/17/)

## Versions
- See [CHANGELOG.md](CHANGELOG.md) 
- 0.5.x (or later) = **Java 17** with new **jakarta** namespace
- 0.3.x/0.4.x = **Java 11** before namespace change from 'javax' to 'jakarta'
- 0.2.1 = **Java 8**


## Bill of Materials (BOM)
The [cqrs-4-java-bom](bom) module is a [Maven BOM](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#bill-of-materials-bom-poms) that manages the versions of all modules of this library. Import it into the `dependencyManagement` section of your project, then declare the modules you need without specifying their version:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fuin.cqrs4j</groupId>
            <artifactId>cqrs-4-java-bom</artifactId>
            <version>0.7.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.fuin.cqrs4j</groupId>
        <artifactId>cqrs-4-java-core</artifactId>
    </dependency>
</dependencies>
```

## Modules

Every module has a `README.md` of its own saying what it is; this is the map. Everything listed here is
managed by the BOM, so none of it needs a version.

| Module | |
|---|---|
| [`core`](core) | The framework-independent heart: commands, results, dispatchers, tenants and authorization. |
| [`esc`](esc) | The bridge to the `esc` event store: projections, leases, freshness and the `CommandMeta` audit record. |
| [`jackson`](jackson) / [`jaxb`](jaxb) / [`jsonb`](jsonb) | The three serialization flavours of commands and results - pick one. |
| [`jpa/command`](jpa/command) | Processed-command entity, so a retry cannot execute a command twice. |
| [`jpa/query`](jpa/query) | Projection position and lease entities. |
| [`jpa/process-manager`](jpa/process-manager) | Outbox, timeout and dead-letter entities. |
| [`test/helper`](test/helper) | Test doubles: containers, a cuttable TCP proxy, a stub OpenID provider, a Keycloak realm fixture, and the shared security ArchUnit rules. |

Not published: [`jacoco`](jacoco) aggregates the coverage report.

## The framework flavours live in their own repositories

Spring Boot and Quarkus used to be two subtrees here. They are the bulk of the code and change on their
own cadence, so they moved out:

| Repository | Artifacts |
|---|---|
| [cqrs-4-springboot](https://github.com/fuinorg/cqrs-4-springboot) | `cqrs-4-java-springboot-*`, `cqrs-4-java-test-springboot` |
| [cqrs-4-quarkus](https://github.com/fuinorg/cqrs-4-quarkus) | `cqrs-4-java-quarkus-*`, `cqrs-4-java-test-quarkus` |

**The coordinates did not change** - the groupId and every artifactId are exactly as before. What changed
is that each repository publishes its own BOM, so a Spring Boot application imports
`cqrs-4-java-springboot-bom` alongside `cqrs-4-java-bom`. Keeping the BOMs apart means neither can name a
version of something released on the other's cadence.

## Securing an application

The filter chain, its `cqrs4j.security.*` properties and the Keycloak token validation all live in
[cqrs-4-springboot](https://github.com/fuinorg/cqrs-4-springboot). What stays here is the test support
both flavours use: [`test/helper`](test/helper) has `SecurityArchRules` (a permit-all chain may exist only
under `@Profile("local")`, and the packaged `application.yml` may not activate it) and `KeycloakRealm` (a
provisioned realm and real tokens, for the cases a stub cannot show).

## Resilience
See [resilience.md](resilience.md) for what happens when the event store or the database is unreachable, and how the timeouts and circuit breakers can be configured.

## Example
See [ddd-cqrs-4-java-example](https://github.com/fuinorg/ddd-cqrs-4-java-example) for example microservices using the classes of this library.

## Snapshots

Snapshots can be found on the [Central Portal Snapshots Repository](https://central.sonatype.com/repository/maven-snapshots/org/fuin "Snapshot Repository").

Add the following to your .m2/settings.xml to enable snapshots in your Maven build:

```xml
<repository>
    <id>central-portal-snapshots</id>
    <name>Central Portal Snapshots</name>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
        <enabled>false</enabled>
    </releases>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```
