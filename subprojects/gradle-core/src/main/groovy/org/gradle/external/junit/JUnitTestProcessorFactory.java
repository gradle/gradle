package org.gradle.external.junit;

import org.gradle.api.testing.fabric.TestProcessResultFactory;
import org.gradle.api.testing.fabric.TestProcessor;
import org.gradle.api.testing.fabric.TestProcessorFactory;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestProcessorFactory implements TestProcessorFactory {

    private ClassLoader sandboxClassLoader;
    private TestProcessResultFactory testProcessResultFactory;
    private Class junit4TestAdapterClass;
    private boolean junit4;

    public void initialize(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory) {
        this.sandboxClassLoader = sandboxClassLoader;
        this.testProcessResultFactory = testProcessResultFactory;

        Class junit4TestAdapterClassCandidate = null;
        try {
            junit4TestAdapterClassCandidate = Class.forName("junit.framework.JUnit4TestAdapter");
        }
        catch (ClassNotFoundException noJunit4Exception) {
            // else JUnit 3
        }
        finally {
            if (junit4TestAdapterClassCandidate == null)
                junit4TestAdapterClass = null;
            else
                junit4TestAdapterClass = junit4TestAdapterClassCandidate;
        }
        junit4 = junit4TestAdapterClass != null;
    }

    public TestProcessor createProcessor() {
        return new JUnitTestProcessor(sandboxClassLoader, testProcessResultFactory, junit4TestAdapterClass, junit4);
    }
}
