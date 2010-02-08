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
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.process.WorkerProcess;
import org.gradle.process.WorkerProcessBuilder;
import org.gradle.process.WorkerProcessFactory;

import java.io.File;

public class ForkingTestClassProcessor implements TestClassProcessor {
    private final WorkerProcessFactory workerFactory;
    private final TestClassProcessorFactory processorFactory;
    private final JavaForkOptions options;
    private final Iterable<File> classPath;
    private final Action<WorkerProcessBuilder> buildConfigAction;
    private TestClassProcessor worker;
    private WorkerProcess workerProcess;
    private TestListener listener;

    public ForkingTestClassProcessor(WorkerProcessFactory workerFactory, TestClassProcessorFactory processorFactory, JavaForkOptions options, Iterable<File> classPath, Action<WorkerProcessBuilder> buildConfigAction) {
        this.workerFactory = workerFactory;
        this.processorFactory = processorFactory;
        this.options = options;
        this.classPath = classPath;
        this.buildConfigAction = buildConfigAction;
    }

    public TestClassProcessorFactory getProcessorFactory() {
        return processorFactory;
    }

    public void startProcessing(TestListener listener) {
        this.listener = listener;
    }

    public void processTestClass(TestClassRunInfo testClass) {
        if (worker == null) {
            WorkerProcessBuilder builder = workerFactory.newProcess();
            builder.applicationClasspath(classPath);
            builder.worker(new TestWorker(processorFactory));
            options.copyTo(builder.getJavaCommand());
            buildConfigAction.execute(builder);
            
            workerProcess = builder.build();
            workerProcess.getConnection().addIncoming(TestListener.class, listener);
            worker = workerProcess.getConnection().addOutgoing(TestClassProcessor.class);

            workerProcess.start();
        }
        worker.processTestClass(testClass);
    }

    public void endProcessing() {
        if (worker != null) {
            worker.endProcessing();
            workerProcess.waitForStop();
        }
    }
}
