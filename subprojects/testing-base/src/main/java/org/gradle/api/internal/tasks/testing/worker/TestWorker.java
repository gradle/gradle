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
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.actor.internal.DefaultActorFactory;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.dispatch.ContextClassLoaderProxy;
import org.gradle.internal.id.CompositeIdGenerator;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.security.AccessControlException;
import java.util.concurrent.CountDownLatch;

public class TestWorker implements Action<WorkerProcessContext>, RemoteTestClassProcessor, Serializable, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWorker.class);
    public static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    private final WorkerTestClassProcessorFactory factory;
    private CountDownLatch completed;
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;

    public TestWorker(WorkerTestClassProcessorFactory factory) {
        this.factory = factory;
    }

    @Override
    public void execute(final WorkerProcessContext workerProcessContext) {
        LOGGER.info("{} started executing tests.", workerProcessContext.getDisplayName());

        SecurityManager securityManager = System.getSecurityManager();
        completed = new CountDownLatch(1);

        System.setProperty(WORKER_ID_SYS_PROPERTY, workerProcessContext.getWorkerId().toString());

        DefaultServiceRegistry testServices = new TestFrameworkServiceRegistry(workerProcessContext);
        startReceivingTests(workerProcessContext, testServices);

        try {
            try {
                completed.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } finally {
            LOGGER.info("{} finished executing tests.", workerProcessContext.getDisplayName());

            if (System.getSecurityManager() != securityManager) {
                try {
                    // Reset security manager the tests seem to have installed
                    System.setSecurityManager(securityManager);
                } catch (SecurityException e) {
                    LOGGER.warn("Unable to reset SecurityManager. Continuing anyway...", e);
                }
            }
            testServices.close();
        }
    }

    private void startReceivingTests(WorkerProcessContext workerProcessContext, ServiceRegistry testServices) {
        TestClassProcessor targetProcessor = factory.create(testServices);
        IdGenerator<Object> idGenerator = Cast.uncheckedNonnullCast(testServices.get(IdGenerator.class));

        targetProcessor = new WorkerTestClassProcessor(targetProcessor, idGenerator.generateId(),
                workerProcessContext.getDisplayName(), testServices.get(Clock.class));
        ContextClassLoaderProxy<TestClassProcessor> proxy = new ContextClassLoaderProxy<TestClassProcessor>(
                TestClassProcessor.class, targetProcessor, workerProcessContext.getApplicationClassLoader());
        processor = proxy.getSource();

        ObjectConnection serverConnection = workerProcessContext.getServerConnection();
        serverConnection.useParameterSerializers(TestEventSerializer.create());
        this.resultProcessor = serverConnection.addOutgoing(TestResultProcessor.class);
        serverConnection.addIncoming(RemoteTestClassProcessor.class, this);
        serverConnection.connect();
    }

    @Override
    public void startProcessing() {
        Thread.currentThread().setName("Test worker");
        processor.startProcessing(resultProcessor);
    }

    @Override
    public void processTestClass(final TestClassRunInfo testClass) {
        Thread.currentThread().setName("Test worker");
        try {
            processor.processTestClass(testClass);
        } catch (AccessControlException e) {
            completed.countDown();
            throw e;
        } finally {
            // Clean the interrupted status
            Thread.interrupted();
        }
    }

    @Override
    public void stop() {
        Thread.currentThread().setName("Test worker");
        try {
            processor.stop();
        } finally {
            completed.countDown();
            // Clean the interrupted status
            // because some test class processors do work here, e.g. JUnitPlatform
            Thread.interrupted();
        }
    }

    private static class TestFrameworkServiceRegistry extends DefaultServiceRegistry {
        private final WorkerProcessContext workerProcessContext;

        public TestFrameworkServiceRegistry(WorkerProcessContext workerProcessContext) {
            this.workerProcessContext = workerProcessContext;
        }

        protected Clock createClock() {
            return workerProcessContext.getServiceRegistry().get(Clock.class);
        }

        protected IdGenerator<Object> createIdGenerator() {
            return new CompositeIdGenerator(workerProcessContext.getWorkerId(), new LongIdGenerator());
        }

        protected ExecutorFactory createExecutorFactory() {
            return new DefaultExecutorFactory();
        }

        protected ActorFactory createActorFactory(ExecutorFactory executorFactory) {
            return new DefaultActorFactory(executorFactory);
        }
    }
}
