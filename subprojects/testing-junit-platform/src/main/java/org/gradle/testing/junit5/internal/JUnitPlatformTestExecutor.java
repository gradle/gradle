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
package org.gradle.testing.junit5.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.worker.WorkerProcess;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.util.CollectionUtils;

import java.net.URL;
import java.util.List;

public class JUnitPlatformTestExecutor implements TestExecuter<JUnitPlatformTestExecutionSpec> {
    private final ModuleRegistry moduleRegistry;
    private final WorkerProcessFactory workerFactory;
    private final WorkerLeaseRegistry workerLeaseRegistry;

    private final JavaForkOptions forkOptions;

    public JUnitPlatformTestExecutor(ModuleRegistry moduleRegistry, WorkerProcessFactory workerFactory, WorkerLeaseRegistry workerLeaseRegistry, JavaForkOptions forkOptions) {
        this.moduleRegistry = moduleRegistry;
        this.workerFactory = workerFactory;
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.forkOptions = forkOptions;
    }

    @Override
    public void execute(final JUnitPlatformTestExecutionSpec testExecutionSpec, TestResultProcessor testResultProcessor) {
        WorkerProcessBuilder builder = workerFactory.create(new Action<WorkerProcessContext>() {
            @Override
            public void execute(WorkerProcessContext workerProcessContext) {
                ObjectConnection serverConnection = workerProcessContext.getServerConnection();
                serverConnection.useJavaSerializationForParameters(Thread.currentThread().getContextClassLoader());
                TestResultProcessor testResultProcessor = serverConnection.addOutgoing(TestResultProcessor.class);
                serverConnection.connect();
            }
        });
        });

        builder.setBaseName("Gradle JUnit Platform Executor");
        builder.setImplementationClasspath(getWorkerImplementationClasspath());
        builder.applicationClasspath(testExecutionSpec.getClasspath());
        forkOptions.copyTo(builder.getJavaCommand());
        builder.getJavaCommand().jvmArgs("-Dorg.gradle.native=false");

        WorkerProcess workerProcess = builder.build();
        workerProcess.start();

        ObjectConnection connection = workerProcess.getConnection();
        connection.useJavaSerializationForParameters(Thread.currentThread().getContextClassLoader());
        connection.addIncoming(TestResultProcessor.class, testResultProcessor);
        connection.connect();
    }

    private List<URL> getWorkerImplementationClasspath() {
        return CollectionUtils.flattenCollections(URL.class,
            moduleRegistry.getModule("gradle-testing-junit-platform").getImplementationClasspath().getAsURLs()
        );
    }
}
