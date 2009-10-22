package org.gradle.api.testing.detection;

import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.testing.fabric.TestFrameworkDetector;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestClassScannerFactory implements TestClassScannerFactory {
    public TestClassScanner createTestClassScanner(AbstractTestTask testTask, TestFrameworkDetector testFrameworkDetector, TestClassProcessor testClassProcessor) {
        return new DefaultTestClassScanner(testTask, testFrameworkDetector, testClassProcessor);
    }
}
