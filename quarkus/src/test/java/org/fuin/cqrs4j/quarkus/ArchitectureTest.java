package org.fuin.cqrs4j.quarkus;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;

/**
 * Tests architectural aspects.
 */
@AnalyzeClasses(packagesOf = ArchitectureTest.class, importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    private static final String THIS_PACKAGE = ArchitectureTest.class.getPackageName() + "..";

    @ArchTest
    static final ArchRule no_accesses_to_upper_package = NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;

    @ArchTest
    static final ArchRule access_only_to_defined_packages = classes()
            .that()
            .resideInAPackage(THIS_PACKAGE)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(THIS_PACKAGE, "java..",
                    "jakarta.enterprise.context..",
                    "jakarta.enterprise.inject..",
                    "org.eclipse.microprofile.config..",
                    "jakarta.inject..",
                    "jakarta.persistence..",
                    "jakarta.validation.constraints..",
                    "org.fuin.cqrs4j.core..",
                    "org.fuin.cqrs4j.esc..",
                    "org.fuin.ddd4j.core..",
                    "org.fuin.esc.api..",
                    "org.fuin.objects4j.common..",
                    "org.fuin.utils4j..",
                    "org.jspecify.annotations..",
                    "org.slf4j..",
                    "io.quarkus.arc..",
                    "io.quarkus.narayana.jta..",
                    "io.quarkus.runtime..",
                    "io.quarkus.scheduler.."
                    );

}
