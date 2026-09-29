package com.vasmarketing.segment;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.vasmarketing.platform.testsupport.HexagonalArchitecture;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  @Test
  void followsHexagonalRules() {
    HexagonalArchitecture.check(
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.vasmarketing.segment"),
        "com.vasmarketing.segment");
  }
}
