package org.gradle.api.testing.detection;

import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.testing.fabric.TestFrameworkDetector;

/**
 * @author Tom Eyckmans
 */
public interface TestClassScannerFactory {
    TestClassScanner createTestClassScanner(AbstractTestTask testTask, TestFrameworkDetector testFrameworkDetector, TestClassProcessor testClassProcessor);
}
