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
import org.gradle.api.internal.tasks.testing.AttachParentTestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessor;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.listener.ContextClassLoaderProxy;
import org.gradle.listener.ThreadSafeProxy;
import org.gradle.messaging.ObjectConnection;
import org.gradle.process.WorkerProcessContext;
import org.gradle.util.CompositeIdGenerator;
import org.gradle.util.IdGenerator;
import org.gradle.util.LongIdGenerator;
import org.gradle.util.TrueTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class TestWorker implements Action<WorkerProcessContext>, TestClassProcessor, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWorker.class);
    private final WorkerTestClassProcessorFactory factory;
    private CountDownLatch completed;
    private TestClassProcessor processor;

    public TestWorker(WorkerTestClassProcessorFactory factory) {
        this.factory = factory;
    }

    public void execute(WorkerProcessContext workerProcessContext) {
        LOGGER.info("{} executing tests.", workerProcessContext.getDisplayName());

        completed = new CountDownLatch(1);

        ObjectConnection serverConnection = workerProcessContext.getServerConnection();

        IdGenerator<Object> idGenerator = new CompositeIdGenerator(workerProcessContext.getWorkerId(), new LongIdGenerator());
        TestClassProcessor targetProcessor = factory.create(idGenerator);
        targetProcessor = new WorkerTestClassProcessor(targetProcessor, idGenerator.generateId(), workerProcessContext.getDisplayName(), new TrueTimeProvider());
        ContextClassLoaderProxy<TestClassProcessor> proxy = new ContextClassLoaderProxy<TestClassProcessor>(TestClassProcessor.class, targetProcessor, workerProcessContext.getApplicationClassLoader());
        processor = proxy.getSource();

        TestResultProcessor resultProcessor = serverConnection.addOutgoing(TestResultProcessor.class);
        resultProcessor = new AttachParentTestResultProcessor(resultProcessor);
        ThreadSafeProxy<TestResultProcessor> resultProcessorProxy = new ThreadSafeProxy<TestResultProcessor>(TestResultProcessor.class, resultProcessor);
        processor.startProcessing(resultProcessorProxy.getSource());

        serverConnection.addIncoming(TestClassProcessor.class, this);

        try {
            completed.await();
        } catch (InterruptedException e) {
            throw new GradleException(e);
        }
        LOGGER.info("{} finished executing tests.", workerProcessContext.getDisplayName());
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        // Unexpected
        throw new UnsupportedOperationException();
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
