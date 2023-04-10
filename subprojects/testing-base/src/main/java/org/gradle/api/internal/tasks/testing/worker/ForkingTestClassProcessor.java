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

import com.google.common.collect.Sets;
import org.apache.commons.lang.NullArgumentException;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassStealer;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.worker.WorkerProcess;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ForkingTestClassProcessor implements TestClassProcessor {
    private final WorkerProcessFactory workerFactory;
    private final WorkerTestClassProcessorFactory processorFactory;
    private final JavaForkOptions options;
    private final ForkedTestClasspath classpath;
    private final Action<WorkerProcessBuilder> buildConfigAction;
    private final Lock lock = new ReentrantLock();
    private final WorkerThreadRegistry workerThreadRegistry;
    @Nullable
    private final TestClassStealer testClassStealer;
    private RemoteTestClassProcessor remoteProcessor;
    private WorkerProcess workerProcess;
    private TestResultProcessor resultProcessor;
    private WorkerLeaseRegistry.WorkerLeaseCompletion completion;
    private final DocumentationRegistry documentationRegistry;
    private boolean stoppedNow;
    private final Set<Throwable> unrecoverableExceptions = Sets.newHashSet();



    public ForkingTestClassProcessor(
        WorkerThreadRegistry workerThreadRegistry,
        WorkerProcessFactory workerFactory,
        WorkerTestClassProcessorFactory processorFactory,
        JavaForkOptions options,
        ForkedTestClasspath classpath,
        Action<WorkerProcessBuilder> buildConfigAction,
        DocumentationRegistry documentationRegistry,
        @Nullable TestClassStealer testClassStealer
    ) {
        this.workerThreadRegistry = workerThreadRegistry;
        this.workerFactory = workerFactory;
        this.processorFactory = processorFactory;
        this.options = options;
        this.classpath = classpath;
        this.buildConfigAction = buildConfigAction;
        this.documentationRegistry = documentationRegistry;
        this.testClassStealer = testClassStealer;
    }

    @Override
    public String toString() {
        return String.format("Processor, worker: %s", workerProcess);
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        this.resultProcessor = resultProcessor;
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        lock.lock();
        try {
            if (stoppedNow) {
                return;
            }

            if (remoteProcessor == null) {
                completion = workerThreadRegistry.startWorker();
                try {
                    remoteProcessor = forkProcess();
                } catch (RuntimeException e) {
                    completion.leaseFinish();
                    completion = null;
                    throw e;
                }
            }
            if (testClassStealer != null) {
                testClassStealer.add(testClass, this);
            }
            remoteProcessor.processTestClass(testClass);
        } finally {
            lock.unlock();
        }
    }

    public void handOver(TestClassRunInfo testClass) {
        if (testClassStealer == null) {
            throw new IllegalStateException();
        }
        lock.lock();
        try {
            if (stoppedNow) {
                testClassStealer.handOverTestClass(this, testClass, false);
            } else {
                remoteProcessor.handOverTestClass(testClass);
            }
        } catch (Exception e) {
            // ignore
            testClassStealer.handOverTestClass(this, testClass, false);
        } finally {
            lock.unlock();
        }
    }

    RemoteTestClassProcessor forkProcess() {
        final RemoteStealer remoteStealer = testClassStealer != null ? new RemoteStealerWorker(this, testClassStealer) : null;
        WorkerProcessBuilder builder = workerFactory.create(new TestWorker(processorFactory, remoteStealer));
        builder.setBaseName("Gradle Test Executor");
        builder.setImplementationClasspath(classpath.getImplementationClasspath());
        builder.setImplementationModulePath(classpath.getImplementationModulepath());
        builder.applicationClasspath(classpath.getApplicationClasspath());
        builder.applicationModulePath(classpath.getApplicationModulepath());
        options.copyTo(builder.getJavaCommand());
        builder.getJavaCommand().jvmArgs("-Dorg.gradle.native=false");
        buildConfigAction.execute(builder);

        workerProcess = builder.build();
        workerProcess.start();

        ObjectConnection connection = workerProcess.getConnection();
        connection.useParameterSerializers(TestEventSerializer.create());
        connection.addUnrecoverableErrorHandler(new Action<Throwable>() {
            @Override
            public void execute(@Nonnull Throwable throwable) {
                lock.lock();
                try {
                    if (!stoppedNow) {
                        unrecoverableExceptions.add(throwable);
                    }
                } finally {
                    lock.unlock();
                }
            }
        });
        connection.addIncoming(TestResultProcessor.class, resultProcessor);
        if (testClassStealer != null) {
            connection.addIncoming(RemoteStealer.class, remoteStealer);
        }
        RemoteTestClassProcessor remoteProcessor = connection.addOutgoing(RemoteTestClassProcessor.class);
        connection.connect();
        remoteProcessor.startProcessing();
        return remoteProcessor;
    }

    @Override
    public void stop() {
        try {
            if (remoteProcessor != null) {
                lock.lock();
                try {
                    if (!stoppedNow) {
                        remoteProcessor.stop();
                    }
                } finally {
                    lock.unlock();
                }
                workerProcess.waitForStop();
                if (testClassStealer != null) {
                    testClassStealer.stopped(this); // release waiting stealer
                }
            }
        } catch (ExecException e) {
            if (!stoppedNow) {
                throw new ExecException(e.getMessage()
                    + "\nThis problem might be caused by incorrect test process configuration."
                    + "\n" + documentationRegistry.getDocumentationRecommendationFor("on test execution", "java_testing", "sec:test_execution"), e.getCause());
            }
        } finally {
            if (completion != null) {
                completion.leaseFinish();
            }
        }

        maybeRethrowUnrecoverableExceptions();
    }

    @Override
    public void stopNow() {
        lock.lock();
        try {
            stoppedNow = true;
            if (remoteProcessor != null) {
                workerProcess.stopNow();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * If there are communication errors while receiving test results from the test worker,
     * we can get in a situation where a test appears skipped even though it actually failed.
     * We want to capture the communication errors and be sure to fail the test execution so
     * as to avoid any false positives.
     */
    private void maybeRethrowUnrecoverableExceptions() {
        if (!unrecoverableExceptions.isEmpty()) {
            throw new DefaultMultiCauseException("Unexpected errors were encountered while processing test results that may result in some results being incorrect or incomplete.", unrecoverableExceptions);
        }
    }

    // theoretical to merge with TestWorker.WorkerTestClassStealer
    @NonNullApi
    public static class RemoteStealerWorker implements RemoteStealer {
        private static final Logger LOGGER = LoggerFactory.getLogger(RemoteStealerWorker.class);
        private final transient ForkingTestClassProcessor processor;
        private final transient TestClassStealer testClassStealer;

        private RemoteStealerWorker(ForkingTestClassProcessor processor, TestClassStealer testClassStealer) {
            this.processor = processor;
            this.testClassStealer = testClassStealer;
        }

        @Override
        public void remove(TestClassRunInfo testClass) {
            testClassStealer.remove(testClass);
        }

        @Override
        public void tryStealing() {
            testClassStealer.stopped(processor); // ensure not stealing own not removed testClasses
            final TestClassRunInfo testClass = testClassStealer.trySteal();
            if (!processor.stoppedNow) {
                processor.remoteProcessor.handedOver(new HandOverResult(testClass, true));
            }
        }

        /**
         * notify {@link #testClassStealer} about hand over result
         */
        @Override
        public void handOver(HandOverResult result) {
            LOGGER.debug("RemoteStealerWorker: handOver {}", result);
            TestClassRunInfo testClass = result.getTestClass();
            if (testClass == null) {
                throw new NullArgumentException("result.testClass");
            }
            testClassStealer.handOverTestClass(processor, testClass, result.isSuccess());
        }
    }
}
