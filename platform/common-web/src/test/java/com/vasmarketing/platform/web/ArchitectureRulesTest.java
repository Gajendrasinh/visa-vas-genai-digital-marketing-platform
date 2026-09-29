package com.vasmarketing.platform.web;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.vasmarketing.platform.web",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  @ArchTest static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  @ArchTest static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
