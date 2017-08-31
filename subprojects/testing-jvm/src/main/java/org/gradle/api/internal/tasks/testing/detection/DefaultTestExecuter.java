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
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.processors.MaxNParallelTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.RestartEveryNTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.internal.tasks.testing.worker.ForkingTestClassProcessor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.Factory;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;
import java.util.Set;

/**
 * The default test class scanner factory.
 */
public class DefaultTestExecuter implements TestExecuter {

    private static final Logger LOGGER = Logging.getLogger(DefaultTestExecuter.class);

    private final WorkerProcessFactory workerFactory;
    private final ActorFactory actorFactory;
    private final ModuleRegistry moduleRegistry;
    private final WorkerLeaseRegistry workerLeaseRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final int maxWorkerCount;
    private final TimeProvider timeProvider;

    public DefaultTestExecuter(WorkerProcessFactory workerFactory, ActorFactory actorFactory, ModuleRegistry moduleRegistry,
                               WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor, int maxWorkerCount,
                               TimeProvider timeProvider) {
        this.workerFactory = workerFactory;
        this.actorFactory = actorFactory;
        this.moduleRegistry = moduleRegistry;
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.maxWorkerCount = maxWorkerCount;
        this.timeProvider = timeProvider;
    }

    @Override
    public void execute(final Test testTask, TestResultProcessor testResultProcessor) {
        final TestFramework testFramework = testTask.getTestFramework();
        final WorkerTestClassProcessorFactory testInstanceFactory = testFramework.getProcessorFactory();
        final WorkerLeaseRegistry.WorkerLease currentWorkerLease = workerLeaseRegistry.getCurrentWorkerLease();
        final Set<File> classpath = ImmutableSet.copyOf(testTask.getClasspath());
        final Factory<TestClassProcessor> forkingProcessorFactory = new Factory<TestClassProcessor>() {
            public TestClassProcessor create() {
                return new ForkingTestClassProcessor(currentWorkerLease, workerFactory, testInstanceFactory, testTask,
                    classpath, testFramework.getWorkerConfigurationAction(), moduleRegistry);
            }
        };
        final Factory<TestClassProcessor> reforkingProcessorFactory = new Factory<TestClassProcessor>() {
            public TestClassProcessor create() {
                return new RestartEveryNTestClassProcessor(forkingProcessorFactory, testTask.getForkEvery());
            }
        };
        TestClassProcessor processor = new MaxNParallelTestClassProcessor(getMaxParallelForks(testTask), reforkingProcessorFactory, actorFactory);

        final FileTree testClassFiles = testTask.getCandidateClassFiles();

        Runnable detector;
        if (testTask.isScanForTestClasses()) {
            TestFrameworkDetector testFrameworkDetector = testFramework.getDetector();
            testFrameworkDetector.setTestClasses(testTask.getTestClassesDirs().getFiles());
            testFrameworkDetector.setTestClasspath(classpath);
            detector = new DefaultTestClassScanner(testClassFiles, testFrameworkDetector, processor);
        } else {
            detector = new DefaultTestClassScanner(testClassFiles, null, processor);
        }

        final Object testTaskOperationId = buildOperationExecutor.getCurrentOperation().getParentId();

        new TestMainAction(detector, processor, testResultProcessor, timeProvider, testTaskOperationId, testTask.getPath(), "Gradle Test Run " + testTask.getIdentityPath()).run();
    }

    private int getMaxParallelForks(Test testTask) {
        int maxParallelForks = testTask.getMaxParallelForks();
        if (maxParallelForks > maxWorkerCount) {
            LOGGER.info("{}.maxParallelForks ({}) is larger than max-workers ({}), forcing it to {}", testTask.getName(), maxParallelForks, maxWorkerCount, maxWorkerCount);
            maxParallelForks = maxWorkerCount;
        }
        return maxParallelForks;
    }
}
