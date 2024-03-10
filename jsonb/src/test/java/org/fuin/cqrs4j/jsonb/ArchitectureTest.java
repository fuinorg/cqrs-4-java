package org.fuin.cqrs4j.jsonb;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.fuin.cqrs4j.core.Command;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;

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
    static final ArchRule access_only_to_defined_packages = classes()
            .that()
            .resideInAPackage(THIS_PACKAGE)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(THIS_PACKAGE, CORE_PACKAGE, "java..",
                    "org.fuin.ddd4j.common..",
                    "org.fuin.ddd4j.core..",
                    "org.fuin.ddd4j.jsonb..",
                    "org.fuin.objects4j.ui..",
                    "org.fuin.objects4j.common..",
                    "org.fuin.objects4j.core..",
                    "jakarta.validation..",
                    "jakarta.json..",
                    "org.slf4j..",
                    "javax.annotation.concurrent.."
                    );

}
