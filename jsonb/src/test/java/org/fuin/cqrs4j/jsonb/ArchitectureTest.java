package org.fuin.cqrs4j.jsonb;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.fuin.cqrs4j.core.Command;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;
import static org.fuin.units4j.archunit.Units4JConditions.ALL_CLASSES_SHOULD_HAVE_A_THREAD_SAFETY_ANNOTATION;

/**
 * Tests architectural aspects.
 */
@AnalyzeClasses(packagesOf = ArchitectureTest.class, importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    private static final String THIS_PACKAGE = ArchitectureTest.class.getPackageName();

    private static final String CORE_PACKAGE = Command.class.getPackageName();

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
            .resideInAnyPackage(THIS_PACKAGE, CORE_PACKAGE, "java..",
                    "org.fuin.ddd4j.common..",
                    "org.fuin.ddd4j.core..",
                    "org.fuin.ddd4j.jsonb..",
                    "org.fuin.esc.api..",
                    "org.fuin.objects4j.jsonb..",
                    "org.fuin.utils4j..",
                    "org.fuin.objects4j.ui..",
                    "org.fuin.objects4j.common..",
                    "org.fuin.objects4j.core..",
                    "jakarta.validation..",
                    "jakarta.annotation..",
                    "jakarta.json..",
                    "org.jspecify.annotations..",
                    "org.slf4j..",
                    "javax.annotation.concurrent.."
                    );

}
