package org.gradle.external.testng;

import org.gradle.api.testing.execution.AbstractTestFrameworkDetector;

import java.io.File;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
class TestNGDetector extends AbstractTestFrameworkDetector<TestNGTestClassDetecter> {
    TestNGDetector(File testClassesDirectory, List<File> testClasspath) {
        super(testClassesDirectory, testClasspath);
    }

    protected TestNGTestClassDetecter createClassVisitor() {
        return new TestNGTestClassDetecter(this);
    }

}
