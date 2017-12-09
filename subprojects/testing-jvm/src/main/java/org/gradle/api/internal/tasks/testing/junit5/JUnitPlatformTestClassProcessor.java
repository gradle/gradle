package org.gradle.api.internal.tasks.testing.junit5;

import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;

public class JUnitPlatformTestClassProcessor implements TestClassProcessor {
    private JUnitPlatformLauncher launcher;

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        this.launcher = new JUnitPlatformLauncher(resultProcessor);
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        launcher.execute(testClass.getTestClassName());
    }

    @Override
    public void stop() {
        // do nothing
    }
}
