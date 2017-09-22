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

package org.gradle.workers.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.LoggingManager;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.MultiRequestWorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcess;
import org.gradle.process.internal.worker.WorkerProcessFactory;

public class WorkerDaemonStarter {
    private final static Logger LOG = Logging.getLogger(WorkerDaemonStarter.class);
    private final WorkerProcessFactory workerDaemonProcessFactory;
    private final LoggingManager loggingManager;

    public WorkerDaemonStarter(WorkerProcessFactory workerDaemonProcessFactory, LoggingManager loggingManager) {
        this.workerDaemonProcessFactory = workerDaemonProcessFactory;
        this.loggingManager = loggingManager;
    }

    public <T extends WorkSpec> WorkerDaemonClient startDaemon(Class<? extends WorkerProtocol<ActionExecutionSpec>> workerProtocolImplementationClass, DaemonForkOptions forkOptions) {
        LOG.debug("Starting Gradle worker daemon with fork options {}.", forkOptions);
        Timer clock = Time.startTimer();
        MultiRequestWorkerProcessBuilder<WorkerDaemonProcess> builder = workerDaemonProcessFactory.multiRequestWorker(WorkerDaemonProcess.class, WorkerProtocol.class, workerProtocolImplementationClass);
        builder.setBaseName("Gradle Worker Daemon");
        builder.setLogLevel(loggingManager.getLevel()); // NOTE: might make sense to respect per-compile-task log level
        builder.applicationClasspath(forkOptions.getClasspath());
        builder.sharedPackages(forkOptions.getSharedPackages());
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        forkOptions.getJavaForkOptions().copyTo(javaCommand);
        WorkerDaemonProcess workerDaemonProcess = builder.build();
        WorkerProcess workerProcess = workerDaemonProcess.start();

        WorkerDaemonClient client = new WorkerDaemonClient(forkOptions, workerDaemonProcess, workerProcess, loggingManager.getLevel());

        LOG.info("Started Gradle worker daemon ({}) with fork options {}.", clock.getElapsed(), forkOptions);

        return client;
    }
}
