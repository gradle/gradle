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

package org.gradle.api.internal.tasks.testing.worker;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.CompositeIdGenerator;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ContextClassLoaderProxy;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.actor.internal.DefaultActorFactory;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.WorkerProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class TestWorker implements Action<WorkerProcessContext>, RemoteTestClassProcessor, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWorker.class);
    public static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    private final WorkerTestClassProcessorFactory factory;
    private CountDownLatch completed;
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;

    public TestWorker(WorkerTestClassProcessorFactory factory) {
        this.factory = factory;
    }

    public void execute(final WorkerProcessContext workerProcessContext) {
        LOGGER.info("{} started executing tests.", workerProcessContext.getDisplayName());

        completed = new CountDownLatch(1);

        System.setProperty(WORKER_ID_SYS_PROPERTY, workerProcessContext.getWorkerId().toString());

        DefaultServiceRegistry testServices = new TestFrameworkServiceRegistry(workerProcessContext);
        startReceivingTests(workerProcessContext, testServices);

        try {
            try {
                completed.await();
            } catch (InterruptedException e) {
                throw new UncheckedException(e);
            }
        } finally {
            LOGGER.info("{} finished executing tests.", workerProcessContext.getDisplayName());
            // Clean out any security manager the tests might have installed
            System.setSecurityManager(null);
            testServices.close();
        }
    }

    private void startReceivingTests(WorkerProcessContext workerProcessContext, ServiceRegistry testServices) {
        TestClassProcessor targetProcessor = factory.create(testServices);
        IdGenerator<Object> idGenerator = testServices.get(IdGenerator.class);

        targetProcessor = new WorkerTestClassProcessor(targetProcessor, idGenerator.generateId(),
                workerProcessContext.getDisplayName(), new TrueTimeProvider());
        ContextClassLoaderProxy<TestClassProcessor> proxy = new ContextClassLoaderProxy<TestClassProcessor>(
                TestClassProcessor.class, targetProcessor, workerProcessContext.getApplicationClassLoader());
        processor = proxy.getSource();

        ObjectConnection serverConnection = workerProcessContext.getServerConnection();
        serverConnection.useParameterSerializer(new TestEventSerializer());
        this.resultProcessor = serverConnection.addOutgoing(TestResultProcessor.class);
        serverConnection.addIncoming(RemoteTestClassProcessor.class, this);
        serverConnection.connect();
    }

    public void startProcessing() {
        Thread.currentThread().setName("Test worker");
        processor.startProcessing(resultProcessor);
    }

    public void processTestClass(final TestClassRunInfo testClass) {
        Thread.currentThread().setName("Test worker");
        try {
            processor.processTestClass(testClass);
        } finally {
            // Clean the interrupted status
            Thread.interrupted();
        }
    }

    public void stop() {
        Thread.currentThread().setName("Test worker");
        try {
            processor.stop();
        } finally {
            completed.countDown();
        }
    }

    private static class TestFrameworkServiceRegistry extends DefaultServiceRegistry {
        private final WorkerProcessContext workerProcessContext;

        public TestFrameworkServiceRegistry(WorkerProcessContext workerProcessContext) {
            this.workerProcessContext = workerProcessContext;
        }

        protected IdGenerator<Object> createIdGenerator() {
            return new CompositeIdGenerator(workerProcessContext.getWorkerId(), new LongIdGenerator());
        }

        protected ExecutorFactory createExecutorFactory() {
            return new DefaultExecutorFactory();
        }

        protected ActorFactory createActorFactory() {
            return new DefaultActorFactory(get(ExecutorFactory.class));
        }
    }
}
