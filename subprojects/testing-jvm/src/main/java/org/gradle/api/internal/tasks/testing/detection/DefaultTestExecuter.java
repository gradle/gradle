/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing.detection;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.FrameworkTestClassProcessor;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.processors.MaxNParallelTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.PatternMatchTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.RestartEveryNTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.RunPreviousFailedFirstTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.internal.tasks.testing.worker.ForkingTestClassProcessor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The default test class scanner factory.
 */
public class DefaultTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private static final Logger LOGGER = Logging.getLogger(DefaultTestExecuter.class);

    private final WorkerProcessFactory workerFactory;
    private final ActorFactory actorFactory;
    private final ModuleRegistry moduleRegistry;
    private final WorkerLeaseService workerLeaseService;
    private final int maxWorkerCount;
    private final Clock clock;
    private final DocumentationRegistry documentationRegistry;
    private final DefaultTestFilter testFilter;
    private TestClassProcessor processor;

    public DefaultTestExecuter(
        WorkerProcessFactory workerFactory, ActorFactory actorFactory, ModuleRegistry moduleRegistry,
        WorkerLeaseService workerLeaseService, int maxWorkerCount,
        Clock clock, DocumentationRegistry documentationRegistry, DefaultTestFilter testFilter
    ) {
        this.workerFactory = workerFactory;
        this.actorFactory = actorFactory;
        this.moduleRegistry = moduleRegistry;
        this.workerLeaseService = workerLeaseService;
        this.maxWorkerCount = maxWorkerCount;
        this.clock = clock;
        this.documentationRegistry = documentationRegistry;
        this.testFilter = testFilter;
    }

    @Override
    public void execute(final JvmTestExecutionSpec testExecutionSpec, TestResultProcessor testResultProcessor) {
        final TestFramework testFramework = testExecutionSpec.getTestFramework();
        final WorkerTestClassProcessorFactory testInstanceFactory = decorateWithRetryer(testExecutionSpec, testFramework.getProcessorFactory());
        final Set<File> classpath = ImmutableSet.copyOf(testExecutionSpec.getClasspath());
        final Set<File> modulePath = ImmutableSet.copyOf(testExecutionSpec.getModulePath());
        final List<String> testWorkerImplementationModules = testFramework.getTestWorkerImplementationModules();
        final Factory<TestClassProcessor> forkingProcessorFactory = new Factory<TestClassProcessor>() {
            @Override
            public TestClassProcessor create() {
                return new ForkingTestClassProcessor(workerLeaseService, workerFactory, testInstanceFactory, testExecutionSpec.getJavaForkOptions(),
                    classpath, modulePath, testWorkerImplementationModules, testFramework.getWorkerConfigurationAction(), moduleRegistry, documentationRegistry);
            }
        };
        final Factory<TestClassProcessor> reforkingProcessorFactory = new Factory<TestClassProcessor>() {
            @Override
            public TestClassProcessor create() {
                return new RestartEveryNTestClassProcessor(forkingProcessorFactory, testExecutionSpec.getForkEvery());
            }
        };
        processor =
            new PatternMatchTestClassProcessor(testFilter,
                new RunPreviousFailedFirstTestClassProcessor(testExecutionSpec.getPreviousFailedTestClasses(),
                    new MaxNParallelTestClassProcessor(getMaxParallelForks(testExecutionSpec), reforkingProcessorFactory, actorFactory)));

        final FileTree testClassFiles = testExecutionSpec.getCandidateClassFiles();

        Runnable detector;
        if (testExecutionSpec.isScanForTestClasses() && testFramework.getDetector() != null) {
            TestFrameworkDetector testFrameworkDetector = testFramework.getDetector();
            testFrameworkDetector.setTestClasses(testExecutionSpec.getTestClassesDirs().getFiles());
            testFrameworkDetector.setTestClasspath(classpath);
            detector = new DefaultTestClassScanner(testClassFiles, testFrameworkDetector, processor);
        } else {
            detector = new DefaultTestClassScanner(testClassFiles, null, processor);
        }

        new TestMainAction(detector, processor, testResultProcessor, workerLeaseService, clock, testExecutionSpec.getPath(), "Gradle Test Run " + testExecutionSpec.getIdentityPath()).run();
    }

    private WorkerTestClassProcessorFactory decorateWithRetryer(final JvmTestExecutionSpec testExecutionSpec, final WorkerTestClassProcessorFactory workerTestClassProcessorFactory) {
        return new RetryableWorkerTestClassProcessorFactory(workerTestClassProcessorFactory, testExecutionSpec.getUntilFailureRunCount());
    }

    public static class RetryableWorkerTestClassProcessorFactory implements WorkerTestClassProcessorFactory, Serializable {

        private final WorkerTestClassProcessorFactory other;
        private final long untilFailureRunCount;

        public RetryableWorkerTestClassProcessorFactory(WorkerTestClassProcessorFactory other, long untilFailureRunCount) {
            this.other = other;
            this.untilFailureRunCount = untilFailureRunCount;
        }

        @Override
        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            final TestClassProcessor frameworkProcessor = other.create(serviceRegistry);
            return new RetryableTestClassProcessor((FrameworkTestClassProcessor) frameworkProcessor, untilFailureRunCount);
        }
    }

    public static class RetryableTestClassProcessor implements TestClassProcessor {
        private final FrameworkTestClassProcessor processor;
        private final long untilFailureRunCount;
        private final AtomicBoolean isRunning;

        public RetryableTestClassProcessor(FrameworkTestClassProcessor processor, long untilFailureRunCount) {
            this.processor = processor;
            this.untilFailureRunCount = untilFailureRunCount;
            this.isRunning = new AtomicBoolean(true);
        }

        @Override
        public void startProcessing(TestResultProcessor resultProcessor) {
            processor.startProcessing(resultProcessor);
        }

        @Override
        public void processTestClass(TestClassRunInfo testClass) {
            processor.processTestClass(testClass);
        }

        @Override
        public void stop() {
            try {
                long executions = Math.max(untilFailureRunCount, 1);
                while (isRunning.get() && executions-- > 0) {
                    processor.runTests();
                }
            } finally {
                processor.stop();
            }
        }

        @Override
        public void stopNow() {
            isRunning.set(false);
            processor.stopNow();
        }
    }

    @Override
    public void stopNow() {
        if (processor != null) {
            processor.stopNow();
        }
    }

    private int getMaxParallelForks(JvmTestExecutionSpec testExecutionSpec) {
        int maxParallelForks = testExecutionSpec.getMaxParallelForks();
        if (maxParallelForks > maxWorkerCount) {
            LOGGER.info("{}.maxParallelForks ({}) is larger than max-workers ({}), forcing it to {}", testExecutionSpec.getPath(), maxParallelForks, maxWorkerCount, maxWorkerCount);
            maxParallelForks = maxWorkerCount;
        }
        return maxParallelForks;
    }
}
