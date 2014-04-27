cqrs-4-java
===========

Command Query Responsibility Segregation for Java
-------------------------------------------------

Base classes for Command Query Responsibility Segregation (CQRS) with Java

[![Build Status](https://fuin-org.ci.cloudbees.com/job/cqrs-4-java/badge/icon)](https://fuin-org.ci.cloudbees.com/job/cqrs-4-java/)

<a href="https://fuin-org.ci.cloudbees.com/job/cqrs-4-java"><img src="http://www.fuin.org/images/Button-Built-on-CB-1.png" width="213" height="72" border="0" alt="Built on CloudBees"/></a>

###Snapshots

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
