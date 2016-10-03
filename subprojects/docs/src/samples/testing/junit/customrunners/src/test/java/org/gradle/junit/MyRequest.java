package org.gradle.junit;

import org.junit.runner.Request;
import org.junit.runner.Runner;

public class MyRequest extends Request {
    private final Object runnerLock = new Object();

    private final Class<?> fTestClass;
    private final boolean canUseSuiteMethod;
    private volatile Runner runner;

    public MyRequest(Class<?> testClass, boolean canUseSuiteMethod) {
        this.fTestClass = testClass;
        this.canUseSuiteMethod = canUseSuiteMethod;
    }

    public MyRequest(Class<?> testClass) {
        this(testClass, true);
    }

    @Override
    public Runner getRunner() {
        if (runner == null) {
            synchronized (runnerLock) {
                if (runner == null) {
                    runner = new MyBuilder(canUseSuiteMethod).safeRunnerForClass(fTestClass);
                }
            }
        }
        return runner;
    }
}
