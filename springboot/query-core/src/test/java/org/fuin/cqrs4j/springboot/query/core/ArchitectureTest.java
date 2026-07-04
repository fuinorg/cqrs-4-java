package org.fuin.cqrs4j.springboot.query.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;
import static org.fuin.units4j.archunit.Units4JConditions.ALL_CLASSES_SHOULD_HAVE_A_THREAD_SAFETY_ANNOTATION;

/**
 * Tests architectural aspects.
 */
@AnalyzeClasses(packagesOf = ArchitectureTest.class, importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    private static final String THIS_PACKAGE = ArchitectureTest.class.getPackageName() + "..";

    @ArchTest
    static final ArchRule no_accesses_to_upper_package = NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;

    @ArchTest
    static final ArchRule all_classes_have_a_thread_safety_annotation = ALL_CLASSES_SHOULD_HAVE_A_THREAD_SAFETY_ANNOTATION;

    @ArchTest
    static final ArchRule access_only_to_defined_packages = classes()
            .that()
            .resideInAPackage(THIS_PACKAGE)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(THIS_PACKAGE, "java..",
                    "io.micrometer..",
                    "javax.sql..",
                    "jakarta.persistence..",
                    "jakarta.validation..",
                    "org.fuin.cqrs4j.core..",
                    "org.fuin.cqrs4j.esc..",
                    "org.fuin.ddd4j.core..",
                    "org.fuin.esc.api..",
                    "org.fuin.objects4j.common..",
                    "org.fuin.utils4j..",
                    "org.jspecify.annotations..",
                    "org.slf4j..",
                    "org.springframework.beans..",
                    "org.springframework.boot..",
                    "org.springframework.context..",
                    "org.springframework.jdbc..",
                    "org.springframework.scheduling..",
                    "org.springframework.stereotype..",
                    "org.springframework.transaction.."
                    );

}
