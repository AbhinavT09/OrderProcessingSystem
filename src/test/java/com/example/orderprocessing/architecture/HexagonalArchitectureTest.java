package com.example.orderprocessing.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests: enforce durable module boundaries that are cheap to regress in refactors.
 * <p>
 * Note: the codebase intentionally allows application-layer references to some infrastructure
 * types (e.g. outbox entities); tightening that is a separate refactor. These rules lock what
 * already holds: pure domain isolation.
 * </p>
 */
@AnalyzeClasses(packages = "com.example.orderprocessing", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domainMustNotDependOnSpringFramework =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");
}
