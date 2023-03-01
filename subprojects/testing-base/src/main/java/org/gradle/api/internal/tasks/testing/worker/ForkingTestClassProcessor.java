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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.worker.WorkerProcess;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;

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
    private RemoteTestClassProcessor remoteProcessor;
    private WorkerProcess workerProcess;
    private TestResultProcessor resultProcessor;
    private WorkerLeaseRegistry.WorkerLeaseCompletion completion;
    private final DocumentationRegistry documentationRegistry;
    private boolean stoppedNow;

    public ForkingTestClassProcessor(
        WorkerThreadRegistry workerThreadRegistry,
        WorkerProcessFactory workerFactory,
        WorkerTestClassProcessorFactory processorFactory,
        JavaForkOptions options,
        ForkedTestClasspath classpath,
        Action<WorkerProcessBuilder> buildConfigAction,
        DocumentationRegistry documentationRegistry
    ) {
        this.workerThreadRegistry = workerThreadRegistry;
        this.workerFactory = workerFactory;
        this.processorFactory = processorFactory;
        this.options = options;
        this.classpath = classpath;
        this.buildConfigAction = buildConfigAction;
        this.documentationRegistry = documentationRegistry;
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

            remoteProcessor.processTestClass(testClass);
        } finally {
            lock.unlock();
        }
    }

    RemoteTestClassProcessor forkProcess() {
        WorkerProcessBuilder builder = workerFactory.create(new TestWorker(processorFactory));
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
        connection.addIncoming(TestResultProcessor.class, resultProcessor);
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
            }
        } catch (ExecException e) {
            if (!stoppedNow) {
                throw new ExecException(e.getMessage()
                    + "\nThis problem might be caused by incorrect test process configuration."
                    + "\nPlease refer to the test execution section in the User Manual at "
                    + documentationRegistry.getDocumentationFor("java_testing", "sec:test_execution"), e.getCause());
            }
        } finally {
            if (completion != null) {
                completion.leaseFinish();
            }
        }
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
}
