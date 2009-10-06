package org.gradle.api.testing.fabric;

import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.util.exec.ExecHandleBuilder;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFrameworkInstance<T extends TestFramework> implements TestFrameworkInstance<T> {
    protected final AbstractTestTask testTask;
    protected final T testFramework;

    protected AbstractTestFrameworkInstance(AbstractTestTask testTask, T testFramework) {
        if (testTask == null) throw new IllegalArgumentException("testTaks == null!");
        if (testFramework == null) throw new IllegalArgumentException("testFramework == null!");

        this.testTask = testTask;
        this.testFramework = testFramework;
    }

    public T getTestFramework() {
        return testFramework;
    }

    protected void useDefaultJvm(ExecHandleBuilder forkHandleBuilder) {
        forkHandleBuilder.execCommand("java");
    }

    protected void useDefaultDirectory(ExecHandleBuilder forkHandleBuilder) {
        forkHandleBuilder.execDirectory(testTask.getProject().getRootDir());
    }
}
