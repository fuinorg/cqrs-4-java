cqrs-4-java
===========

Command Query Responsibility Segregation for Java
-------------------------------------------------

Base classes for Command Query Responsibility Segregation (CQRS) with Java

[![Build Status](https://jenkins.fuin.org/job/cqrs-4-java/badge/icon)](https://jenkins.fuin.org/job/cqrs-4-java/)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.fuin%3Acqrs-4-java&metric=coverage)](https://sonarcloud.io/dashboard?id=org.fuin%3Acqrs-4-java)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.fuin/cqrs-4-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.fuin/cqrs-4-java/)
[![LGPLv3 License](http://img.shields.io/badge/license-LGPLv3-blue.svg)](https://www.gnu.org/licenses/lgpl.html)
[![Java Development Kit 11](https://img.shields.io/badge/JDK-11-green.svg)](https://openjdk.java.net/projects/jdk/11/)

**Java 11 for releases >= 0.3.0** / Java 8 <= 0.2.1


Example
-------
See [ddd-cqrs-4-java-example](https://github.com/fuinorg/ddd-cqrs-4-java-example) for example microservices using the classes of this library.

### Snapshots

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

