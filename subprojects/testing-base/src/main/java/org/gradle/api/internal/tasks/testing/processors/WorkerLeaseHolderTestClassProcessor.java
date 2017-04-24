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
package org.gradle.api.internal.tasks.testing.processors;

import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.Factory;
import org.gradle.internal.work.WorkerLeaseRegistry;

public class WorkerLeaseHolderTestClassProcessor implements TestClassProcessor {

    private final WorkerLeaseRegistry.WorkerLease parentWorkerLease;
    private final Factory<TestClassProcessor> factory;

    private TestClassProcessor processor;

    public WorkerLeaseHolderTestClassProcessor(WorkerLeaseRegistry.WorkerLease parentWorkerLease, Factory<TestClassProcessor> factory) {
        this.parentWorkerLease = parentWorkerLease;
        this.factory = factory;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        processor = factory.create();
        processor.startProcessing(resultProcessor);
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        WorkerLeaseRegistry.WorkerLeaseCompletion workerLease = parentWorkerLease.startChild();
        try {
            processor.processTestClass(testClass);
        } finally {
            workerLease.leaseFinish();
        }
    }

    @Override
    public void stop() {
        processor.stop();
    }
}
