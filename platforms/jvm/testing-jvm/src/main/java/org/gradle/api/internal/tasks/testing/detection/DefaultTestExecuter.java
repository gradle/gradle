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

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestDefinitionProcessorFactory;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.processors.MaxNParallelTestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.processors.PatternMatchTestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.processors.RestartEveryNTestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.processors.RunPreviousFailedFirstTestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.internal.tasks.testing.results.TestRetryShieldingTestResultProcessor;
import org.gradle.api.internal.tasks.testing.worker.ForkedTestClasspath;
import org.gradle.api.internal.tasks.testing.worker.ForkingTestDefinitionProcessor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * The default test class scanner factory.
 */
public class DefaultTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private static final Logger LOGGER = Logging.getLogger(DefaultTestExecuter.class);

    private final WorkerProcessFactory workerFactory;
    private final ActorFactory actorFactory;
    private final ForkedTestClasspathFactory testClasspathFactory;
    private final WorkerLeaseService workerLeaseService;
    private final int maxWorkerCount;
    private final Clock clock;
    private final DefaultTestFilter testFilter;
    private TestDefinitionProcessor<TestDefinition> processor;

    public DefaultTestExecuter(
        WorkerProcessFactory workerFactory, ActorFactory actorFactory, ModuleRegistry moduleRegistry,
        WorkerLeaseService workerLeaseService, int maxWorkerCount,
        Clock clock, DefaultTestFilter testFilter
    ) {
        this.workerFactory = workerFactory;
        this.actorFactory = actorFactory;
        this.testClasspathFactory = new ForkedTestClasspathFactory(moduleRegistry);
        this.workerLeaseService = workerLeaseService;
        this.maxWorkerCount = maxWorkerCount;
        this.clock = clock;
        this.testFilter = testFilter;
    }

    @Override
    public void execute(final JvmTestExecutionSpec testExecutionSpec, TestResultProcessor testResultProcessor) {
        final TestFramework testFramework = testExecutionSpec.getTestFramework();
        // Cast away from ? so we don't need to propagate the wildcard everywhere
        // This is safe because the frameworks that don't accept all TestDefinitions will have the dir selection filtered out earlier
        // If a TestFramework begins to reject ClassTestDefinitions, this needs rethinking.
        final WorkerTestDefinitionProcessorFactory<TestDefinition> testInstanceFactory = Cast.uncheckedNonnullCast(
            testFramework.getProcessorFactory()
        );

        ForkedTestClasspath classpath = testClasspathFactory.create(
            testExecutionSpec.getClasspath(),
            testExecutionSpec.getModulePath()
        );

        final Factory<TestDefinitionProcessor<TestDefinition>> forkingProcessorFactory = () -> new ForkingTestDefinitionProcessor<>(workerLeaseService, workerFactory, testInstanceFactory, testExecutionSpec.getJavaForkOptions(), classpath, testFramework.getWorkerConfigurationAction());
        final Factory<TestDefinitionProcessor<TestDefinition>> reforkingProcessorFactory = () -> new RestartEveryNTestDefinitionProcessor<>(forkingProcessorFactory, testExecutionSpec.getForkEvery());
        processor =
            new PatternMatchTestDefinitionProcessor<>(testFilter,
                new RunPreviousFailedFirstTestDefinitionProcessor<>(testExecutionSpec.getPreviousFailedTestClasses(), Collections.emptySet(),
                    new MaxNParallelTestDefinitionProcessor<>(getMaxParallelForks(testExecutionSpec), reforkingProcessorFactory, actorFactory)));

        final FileTree testClassFiles = testExecutionSpec.getCandidateClassFiles();
        final Set<File> testDefinitionDirs = testExecutionSpec.getCandidateTestDefinitionDirs();

        if (testFramework.getDetector() != null) {
            TestFrameworkDetector testFrameworkDetector = testFramework.getDetector();
            testFrameworkDetector.setTestClasses(new ArrayList<>(testExecutionSpec.getTestClassesDirs().getFiles()));
            testFrameworkDetector.setTestClasspath(classpath.getApplicationClasspath());
        }

        TestDetector detector = new DefaultTestScanner(testClassFiles, testDefinitionDirs, testFramework.getDetector(), processor);

        // What is this?
        // In some versions of the Gradle retry plugin, it would retry any test that had any kind of failure associated with it.
        // We attempt to capture assumption violations as failures for skipped tests.
        //
        // This would cause any test that had been skipped to be executed multiple times. This could sometimes cause real failures.
        // To work around this, we shield the test retry result processor from seeing test assumption failures.
        if (testResultProcessor != null) {
            // KMP calls this code with a delegating test result processor that does not return sensible Class objects
            String canonicalName = testResultProcessor.getClass().getCanonicalName();
            if (canonicalName != null && canonicalName.endsWith("org.gradle.testretry.internal.executer.RetryTestResultProcessor")) {
                testResultProcessor = new TestRetryShieldingTestResultProcessor(testResultProcessor);
            }
        }
        new TestMainAction(detector, processor, testResultProcessor, workerLeaseService, clock, testExecutionSpec.getPath(), "Gradle Test Run " + testExecutionSpec.getIdentityPath()).run();
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
