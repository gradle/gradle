package org.gradle.api.testing.fabric;

/**
 * @author Tom Eyckmans
 */
public interface TestProcessorFactory {
    void initialize(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory);

    TestProcessor createProcessor();
}
