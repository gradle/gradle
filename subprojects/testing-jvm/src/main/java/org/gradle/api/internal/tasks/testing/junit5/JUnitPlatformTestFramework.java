package org.gradle.api.internal.tasks.testing.junit5;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.junit5.JUnitPlatformOptions;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.Serializable;

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
        return new TestClassProcessorFactoryImpl();
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            @Override
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("org.junit.platform");
                workerProcessBuilder.sharedPackages("org.apiguardian");
            }
        };
    }

    private static class TestClassProcessorFactoryImpl implements  WorkerTestClassProcessorFactory, Serializable {
        @Override
        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            return new JUnitPlatformTestClassProcessor(serviceRegistry.get(Clock.class));
        }
    }
}
