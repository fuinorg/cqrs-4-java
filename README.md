# cqrs-4-java

# Command Query Responsibility Segregation for Java
Base classes for Command Query Responsibility Segregation (CQRS) with Java

[![Java Maven Build](https://github.com/fuinorg/cqrs-4-java/actions/workflows/maven.yml/badge.svg)](https://github.com/fuinorg/cqrs-4-java/actions/workflows/maven.yml)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.fuin%3Acqrs-4-java&metric=coverage)](https://sonarcloud.io/dashboard?id=org.fuin%3Acqrs-4-java)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.fuin/cqrs-4-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.fuin/cqrs-4-java/)
[![LGPLv3 License](http://img.shields.io/badge/license-LGPLv3-blue.svg)](https://www.gnu.org/licenses/lgpl.html)
[![Java Development Kit 17](https://img.shields.io/badge/JDK-17-green.svg)](https://openjdk.java.net/projects/jdk/17/)

## Versions
- 0.5.x (or later) = **Java 17** with new **jakarta** namespace
- 0.3.x/0.4.x = **Java 11** before namespace change from 'javax' to 'jakarta'
- 0.2.1 = **Java 8**


## Example
See [ddd-cqrs-4-java-example](https://github.com/fuinorg/ddd-cqrs-4-java-example) for example microservices using the classes of this library.

## Snapshots

Snapshots can be found on the [OSS Sonatype Snapshots Repository](http://oss.sonatype.org/content/repositories/snapshots/org/fuin "Snapshot Repository"). 

Add the following to your [.m2/settings.xml](http://maven.apache.org/ref/3.2.1/maven-settings/settings.html "Reference configuration") to enable snapshots in your Maven build:

```xml
<repository>
    <id>sonatype.oss.snapshots</id>
    <name>Sonatype OSS Snapshot Repository</name>
    <url>http://oss.sonatype.org/content/repositories/snapshots</url>
    <releases>
        <enabled>false</enabled>
    </releases>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

