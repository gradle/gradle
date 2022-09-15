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
import org.gradle.api.internal.classpath.ModuleRegistry;
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
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ForkingTestClassProcessor implements TestClassProcessor {
    private final WorkerProcessFactory workerFactory;
    private final WorkerTestClassProcessorFactory processorFactory;
    private final JavaForkOptions options;
    private final Iterable<File> classPath;
    private final Iterable<File> modulePath;
    private final List<String> testWorkerImplementationModules;
    private final Action<WorkerProcessBuilder> buildConfigAction;
    private final ModuleRegistry moduleRegistry;
    private final Lock lock = new ReentrantLock();
    private final WorkerThreadRegistry workerThreadRegistry;
    private RemoteTestClassProcessor remoteProcessor;
    private WorkerProcess workerProcess;
    private TestResultProcessor resultProcessor;
    private WorkerLeaseRegistry.WorkerLeaseCompletion completion;
    private final DocumentationRegistry documentationRegistry;
    private boolean stoppedNow;

    public ForkingTestClassProcessor(
        WorkerThreadRegistry workerThreadRegistry, WorkerProcessFactory workerFactory, WorkerTestClassProcessorFactory processorFactory, JavaForkOptions options,
        Iterable<File> classPath, Iterable<File> modulePath, List<String> testWorkerImplementationModules,
        Action<WorkerProcessBuilder> buildConfigAction, ModuleRegistry moduleRegistry, DocumentationRegistry documentationRegistry
    ) {
        this.workerThreadRegistry = workerThreadRegistry;
        this.workerFactory = workerFactory;
        this.processorFactory = processorFactory;
        this.options = options;
        this.classPath = classPath;
        this.modulePath = modulePath;
        this.testWorkerImplementationModules = testWorkerImplementationModules;
        this.buildConfigAction = buildConfigAction;
        this.moduleRegistry = moduleRegistry;
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
        @SuppressWarnings("deprecation") // WorkerProcessBuilder#useLegacyAddOpens
        WorkerProcessBuilder builder =
            workerFactory.create(new TestWorker(processorFactory))
                         .setUseLegacyAddOpens(false);
        builder.setBaseName("Gradle Test Executor");
        builder.setImplementationClasspath(getTestWorkerImplementationClasspath());
        builder.setImplementationModulePath(getTestWorkerImplementationModulePath());
        builder.applicationClasspath(classPath);
        builder.applicationModulePath(modulePath);
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

    List<URL> getTestWorkerImplementationClasspath() {
        return CollectionUtils.flattenCollections(URL.class,
            moduleRegistry.getModule("gradle-core-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-worker-processes").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-core").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-logging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-logging-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-messaging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-files").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-file-temp").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-hashing").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-base-services").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-enterprise-logging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-enterprise-workers").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-cli").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-native").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-base").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-jvm").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-junit-platform").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-process-services").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-build-operations").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("slf4j-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("jul-to-slf4j").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("native-platform").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("kryo").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("commons-lang").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("junit").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("javax.inject").getImplementationClasspath().getAsURLs()
        );
    }

    List<URL> getTestWorkerImplementationModulePath() {
        List<URL> modules = new ArrayList<URL>();
        for (String moduleName : testWorkerImplementationModules) {
            modules.addAll(moduleRegistry.getExternalModule(moduleName).getImplementationClasspath().getAsURLs());
        }
        return modules;
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
