# cqrs-4-java

# Command Query Responsibility Segregation for Java
Base classes for Command Query Responsibility Segregation (CQRS) with Java

[![Java Maven Build](https://github.com/fuinorg/cqrs-4-java/actions/workflows/maven.yml/badge.svg)](https://github.com/fuinorg/cqrs-4-java/actions/workflows/maven.yml)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.fuin.cqrs4j%3Acqrs-4-java&metric=coverage)](https://sonarcloud.io/dashboard?id=org.fuin.cqrs4j%3Acqrs-4-java)
[![Maven Central](https://img.shields.io/maven-central/v/org.fuin/cqrs-4-java.svg)](https://central.sonatype.com/artifact/org.fuin/cqrs-4-java)
[![LGPLv3 License](http://img.shields.io/badge/license-LGPLv3-blue.svg)](https://www.gnu.org/licenses/lgpl.html)
[![Java Development Kit 17](https://img.shields.io/badge/JDK-17-green.svg)](https://openjdk.java.net/projects/jdk/17/)

## Versions
- [0.7.0](CHANGELOG.md#070) **Incompatible** Refactored view classes
- [0.6.0](CHANGELOG.md#060) Added new [Jackson](jackson) module
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

The BOM manages the following modules:
- `cqrs-4-java-core`
- `cqrs-4-java-esc`
- `cqrs-4-java-jaxb`
- `cqrs-4-java-jsonb`
- `cqrs-4-java-jackson`
- `cqrs-4-java-springboot`
- `cqrs-4-java-springboot-query-starter`
- `cqrs-4-java-quarkus`

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
