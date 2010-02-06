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
package org.gradle.api.testing.execution.fork;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.messaging.ObjectConnection;
import org.gradle.process.WorkerProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class TestWorker implements Action<WorkerProcessContext>, TestClassProcessor, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWorker.class);
    private final TestClassProcessorFactory factory;
    private CountDownLatch completed;
    private TestClassProcessor processor;

    public TestWorker(TestClassProcessorFactory factory) {
        this.factory = factory;
    }

    public void execute(WorkerProcessContext workerProcessContext) {
        LOGGER.info("Executing tests.");
        completed = new CountDownLatch(1);

        ObjectConnection serverConnection = workerProcessContext.getServerConnection();
        processor = factory.create();

        TestListener listener = serverConnection.addOutgoing(TestListener.class);
        processor.startProcessing(listener);
        
        serverConnection.addIncoming(TestClassProcessor.class, this);

        try {
            completed.await();
        } catch (InterruptedException e) {
            throw new GradleException(e);
        }
    }

    public void startProcessing(TestListener listener) {
    }

    public void processTestClass(TestClassRunInfo testClass) {
        processor.processTestClass(testClass);
    }

    public void endProcessing() {
        try {
            processor.endProcessing();
        } finally {
            completed.countDown();
        }
    }
}
