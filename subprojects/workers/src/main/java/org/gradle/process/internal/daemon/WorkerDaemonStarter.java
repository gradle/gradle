/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.process.internal.daemon;

import org.gradle.StartParameter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.MultiRequestWorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;

public class WorkerDaemonStarter {
    private final static Logger LOG = Logging.getLogger(WorkerDaemonStarter.class);
    private final BuildOperationWorkerRegistry buildOperationWorkerRegistry;
    private final WorkerProcessFactory workerFactory;
    private final StartParameter startParameter;

    public WorkerDaemonStarter(BuildOperationWorkerRegistry buildOperationWorkerRegistry, WorkerProcessFactory workerFactory, StartParameter startParameter) {
        this.buildOperationWorkerRegistry = buildOperationWorkerRegistry;
        this.workerFactory = workerFactory;
        this.startParameter = startParameter;
    }

    public WorkerDaemonClient startDaemon(Class<? extends WorkerDaemonProtocol> serverImplementationClass, File workingDir, DaemonForkOptions forkOptions) {
        LOG.debug("Starting Gradle worker daemon with fork options {}.", forkOptions);
        Timer clock = Timers.startTimer();
        MultiRequestWorkerProcessBuilder<WorkerDaemonWorker> builder = workerFactory.multiRequestWorker(WorkerDaemonWorker.class, WorkerDaemonProtocol.class, serverImplementationClass);
        builder.setBaseName("Gradle Worker Daemon");
        builder.setLogLevel(startParameter.getLogLevel()); // NOTE: might make sense to respect per-compile-task log level
        builder.applicationClasspath(forkOptions.getClasspath());
        builder.sharedPackages(forkOptions.getSharedPackages());
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setMinHeapSize(forkOptions.getMinHeapSize());
        javaCommand.setMaxHeapSize(forkOptions.getMaxHeapSize());
        javaCommand.setJvmArgs(forkOptions.getJvmArgs());
        javaCommand.setWorkingDir(workingDir);
        WorkerDaemonWorker worker = builder.build();
        worker.start();

        WorkerDaemonClient client = new WorkerDaemonClient(buildOperationWorkerRegistry, forkOptions, worker);

        LOG.info("Started Gradle worker daemon ({}) with fork options {}.", clock.getElapsed(), forkOptions);

        return client;
    }
}
