/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.worker.WorkerTestClassProcessor;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TestWorkerProgressListener implements TestListenerInternal {

    private final ProgressLoggerFactory factory;
    private final ProgressLogger parentProgressLogger;
    private final Map<String, ProgressLogger> testWorkerProgressLoggers = new HashMap<String, ProgressLogger>();

    public TestWorkerProgressListener(ProgressLoggerFactory factory, ProgressLogger parentProgressLogger) {
        this.factory = factory;
        this.parentProgressLogger = parentProgressLogger;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        String description = determineTestWorkerDescription(testDescriptor);

        if (description != null && !testWorkerProgressLoggers.containsKey(description)) {
            ProgressLogger progressLogger = factory.newOperation(TestWorkerProgressListener.class, parentProgressLogger);
            progressLogger.start(description, description);
            testWorkerProgressLoggers.put(description, progressLogger);
        }
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        String description = determineTestWorkerDescription(testDescriptor);

        if (description != null && testWorkerProgressLoggers.containsKey(description)) {
            ProgressLogger progressLogger = testWorkerProgressLoggers.remove(description);
            progressLogger.completed();
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
    }

    private String determineTestWorkerDescription(TestDescriptorInternal testDescriptor) {
        WorkerTestClassProcessor.WorkerTestSuiteDescriptor workerTestSuiteDescriptor = findParentWorkerTestSuiteDescriptor(testDescriptor);

        if (workerTestSuiteDescriptor != null) {
            DefaultTestClassDescriptor defaultTestClassDescriptor = findParentDefaultTestClassDescriptor(testDescriptor);

            if (defaultTestClassDescriptor != null) {
                return createProgressLoggerDescription(workerTestSuiteDescriptor, defaultTestClassDescriptor);
            }
        }

        return null;
    }

    private String createProgressLoggerDescription(WorkerTestClassProcessor.WorkerTestSuiteDescriptor workerTestSuiteDescriptor, DefaultTestClassDescriptor defaultTestClassDescriptor) {
        return workerTestSuiteDescriptor.getName() + " > Executing test " + defaultTestClassDescriptor.getClassName();
    }

    private WorkerTestClassProcessor.WorkerTestSuiteDescriptor findParentWorkerTestSuiteDescriptor(TestDescriptor testDescriptor) {
        return findDescriptorParent(testDescriptor, WorkerTestClassProcessor.WorkerTestSuiteDescriptor.class);
    }

    private DefaultTestClassDescriptor findParentDefaultTestClassDescriptor(TestDescriptor testDescriptor) {
        return findDescriptorParent(testDescriptor, DefaultTestClassDescriptor.class);
    }

    private <T> T findDescriptorParent(TestDescriptor testDescriptor, Class<? extends T> descriptorClass) {
        if (testDescriptor == null) {
            return null;
        }

        if (testDescriptor instanceof DecoratingTestDescriptor) {
            DecoratingTestDescriptor decoratingTestDescriptor = (DecoratingTestDescriptor)testDescriptor;
            TestDescriptorInternal actualTestDescriptor = decoratingTestDescriptor.getDescriptor();

            if (descriptorClass.isInstance(actualTestDescriptor)) {
                return Cast.cast(descriptorClass, actualTestDescriptor);
            }
        }

        return findDescriptorParent(testDescriptor.getParent(), descriptorClass);
    }
}
