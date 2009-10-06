package org.gradle.external.testng;

import org.gradle.api.testing.fabric.AbstractTestProcessor;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.fabric.TestProcessResultFactory;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestProcessor extends AbstractTestProcessor {
    protected TestNGTestProcessor(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory) {
        super(sandboxClassLoader, testProcessResultFactory);
    }

    public TestClassProcessResult process(TestClassRunInfo testClassRunInfo) {
        TestClassProcessResult testProcessResult = null;

        // TODO

        return testProcessResult;
    }
}
