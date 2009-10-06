package org.gradle.api.testing.fabric;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestProcessor implements TestProcessor {

    protected final ClassLoader sandboxClassLoader;
    protected final TestProcessResultFactory testProcessResultFactory;

    protected AbstractTestProcessor(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory) {
        this.sandboxClassLoader = sandboxClassLoader;
        this.testProcessResultFactory = testProcessResultFactory;
    }
}
