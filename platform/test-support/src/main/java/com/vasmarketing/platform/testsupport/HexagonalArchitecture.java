package com.vasmarketing.platform.testsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.List;

/**
 * The architecture rules every Java service enforces (docs/architecture.md §7). Services call
 * {@link #check(JavaClasses, String)} from an ArchUnit test.
 */
public final class HexagonalArchitecture {

  private HexagonalArchitecture() {}

  public static List<ArchRule> rules(String basePackage) {
    String domain = basePackage + ".domain..";
    String application = basePackage + ".application..";
    String infrastructure = basePackage + ".infrastructure..";
    String interfaces = basePackage + ".interfaces..";

    ArchRule layers =
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            // A service may not have every layer yet (e.g. no REST interfaces before Phase 3).
            .withOptionalLayers(true)
            .layer("Domain")
            .definedBy(domain)
            .layer("Application")
            .definedBy(application)
            .layer("Infrastructure")
            .definedBy(infrastructure)
            .layer("Interfaces")
            .definedBy(interfaces)
            .whereLayer("Interfaces")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces");

    ArchRule domainIsFrameworkFree =
        classes()
            .that()
            .resideInAPackage(domain)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(domain, "java..", "com.vasmarketing.platform.types..")
            .as("domain depends only on the JDK and platform value types");

    ArchRule persistenceStaysInInfrastructure =
        noClasses()
            .that()
            .resideOutsideOfPackage(infrastructure)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
            .as("JPA/Hibernate types are used only in infrastructure");

    return List.of(
        layers,
        domainIsFrameworkFree,
        persistenceStaysInInfrastructure,
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION,
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING,
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS);
  }

  public static void check(JavaClasses classes, String basePackage) {
    rules(basePackage).forEach(rule -> rule.check(classes));
  }
}
