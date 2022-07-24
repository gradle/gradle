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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Processes tests in a remote process with the given {@link TestClassProcessor} until a stop command is received.  Requires that
 * methods willed be called sequentially in the following order:
 *
 * - {@link RemoteTestClassProcessor#startProcessing()}
 * - 0 or more calls to {@link RemoteTestClassProcessor#processTestClass(TestClassRunInfo)}
 * - {@link RemoteTestClassProcessor#stop()}
 *
 * Commands are received on communication threads and then processed sequentially on the main thread.  Although concurrent calls to
 * any of the methods from {@link RemoteTestClassProcessor} are supported, the commands will still be executed sequentially in the
 * main thread in order of arrival.
 */
public class TestWorker implements Action<WorkerProcessContext>, RemoteTestClassProcessor, Serializable, Stoppable {
    private enum State { INITIALIZING, STARTED, STOPPED }

    private static final Logger LOGGER = LoggerFactory.getLogger(TestWorker.class);
    public static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    public static final String WORKER_TMPDIR_SYS_PROPERTY = "org.gradle.internal.worker.tmpdir";
    private static final String WORK_THREAD_NAME = "Test worker";

    private final WorkerTestClassProcessorFactory factory;
    private final BlockingQueue<Runnable> runQueue = new ArrayBlockingQueue<Runnable>(1);
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;

    /**
     * Note that the state object is not synchronized and not thread-safe.  Any modifications to the
     * the state should ONLY be made inside the main thread or inside a command passed to the run queue
     * (which will execute on the main thread).
     */
    private volatile State state = State.INITIALIZING;

    public TestWorker(WorkerTestClassProcessorFactory factory) {
        this.factory = factory;
    }

    @Override
    public void execute(final WorkerProcessContext workerProcessContext) {
        Thread.currentThread().setName(WORK_THREAD_NAME);

        LOGGER.info("{} started executing tests.", workerProcessContext.getDisplayName());

        SecurityManager securityManager = System.getSecurityManager();

        System.setProperty(WORKER_ID_SYS_PROPERTY, workerProcessContext.getWorkerId().toString());

        DefaultServiceRegistry testServices = new TestFrameworkServiceRegistry(workerProcessContext);
        startReceivingTests(workerProcessContext, testServices);

        try {
            try {
                while (state != State.STOPPED) {
                    executeAndMaintainThreadName(runQueue.take());
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } finally {
            LOGGER.info("{} finished executing tests.", workerProcessContext.getDisplayName());

            // In the event that the main thread exits with an uncaught exception, stop processing
            // and clear out the run queue to unblock any running communication threads
            synchronized(this) {
                state = State.STOPPED;
                runQueue.clear();
            }

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

    private static void executeAndMaintainThreadName(Runnable action) {
        try {
            action.run();
        } finally {
            // Reset the thread name if the action changes it (e.g. if a test sets the thread name without resetting it afterwards)
            Thread.currentThread().setName(WORK_THREAD_NAME);
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
        submitToRun(new Runnable() {
            @Override
            public void run() {
                if (state != State.INITIALIZING) {
                    throw new IllegalStateException("A command to start processing has already been received");
                }
                processor.startProcessing(resultProcessor);
                state = State.STARTED;
            }
        });
    }

    @Override
    public void processTestClass(final TestClassRunInfo testClass) {
        submitToRun(new Runnable() {
            @Override
            public void run() {
                if (state != State.STARTED) {
                    throw new IllegalStateException("Test classes cannot be processed until a command to start processing has been received");
                }
                try {
                    processor.processTestClass(testClass);
                } catch (AccessControlException e) {
                    throw e;
                } finally {
                    // Clean the interrupted status
                    Thread.interrupted();
                }
            }
        });
    }

    @Override
    public void stop() {
        submitToRun(new Runnable() {
            @Override
            public void run() {
                try {
                    processor.stop();
                } finally {
                    state = State.STOPPED;
                    // Clean the interrupted status
                    // because some test class processors do work here, e.g. JUnitPlatform
                    Thread.interrupted();
                }
            }
        });
    }

    private synchronized void submitToRun(Runnable command) {
        if (state != State.STOPPED) {
            try {
                runQueue.put(command);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
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
