package org.fuin.cqrs4j.quarkus.pm;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.fuin.units4j.archunit.Units4JConditions;

@AnalyzeClasses(packagesOf = BaseTest.class)
class BaseTest {

    @ArchTest
    static final ArchRule all_classes_should_have_tests = Units4JConditions.ALL_CLASSES_SHOULD_HAVE_TESTS;

}
