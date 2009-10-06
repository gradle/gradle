package org.gradle.external.testng;

import org.gradle.api.tasks.testing.AbstractTestFramework;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.api.testing.fabric.TestProcessorFactory;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFramework extends AbstractTestFramework {

    public TestNGTestFramework() {
        super("testng", "TestNG");
    }

    public TestFrameworkInstance getInstance(AbstractTestTask testTask) {
        return new TestNGTestFrameworkInstance(testTask, this);
    }

    public TestProcessorFactory getProcessorFactory() {
        return new TestNGTestProcessorFactory();
    }
}
