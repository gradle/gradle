package org.gradle.external.testng;

import org.gradle.api.testing.fabric.TestProcessResultFactory;
import org.gradle.api.testing.fabric.TestProcessor;
import org.gradle.api.testing.fabric.TestProcessorFactory;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestProcessorFactory implements TestProcessorFactory {
    private ClassLoader sandboxClassLoader;
    private TestProcessResultFactory testProcessResultFactory;

    public void initialize(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory) {
        this.sandboxClassLoader = sandboxClassLoader;
        this.testProcessResultFactory = testProcessResultFactory;
    }

    public TestProcessor createProcessor() {
        return new TestNGTestProcessor(sandboxClassLoader, testProcessResultFactory);
    }
}
