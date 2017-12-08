package org.gradle.api.internal.tasks.testing.junit5;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junit5.JUnitPlatformOptions;
import org.gradle.internal.Actions;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

public class JUnitPlatformTestFramework implements TestFramework {
    private JUnitPlatformOptions options;
    private final DefaultTestFilter filter;

    public JUnitPlatformTestFramework(DefaultTestFilter filter) {
        this.options = new JUnitPlatformOptions();
        this.filter = filter;
    }

    @Override
    public TestFrameworkDetector getDetector() {
        throw new UnsupportedOperationException("Disable scanForTestClasses to use JUnit Platform");
    }

    @Override
    public JUnitPlatformOptions getOptions() {
        return options;
    }

    public void setOptions(JUnitPlatformOptions options) {
        this.options = options;
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {

    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return Actions.doNothing();
    }
}
