/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.StartParameter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.Clock;

import java.io.File;

public class CompilerDaemonStarter {
    private final static Logger LOG = Logging.getLogger(CompilerDaemonStarter.class);
    private final Factory<WorkerProcessBuilder> workerFactory;
    private final StartParameter startParameter;

    public CompilerDaemonStarter(Factory<WorkerProcessBuilder> workerFactory, StartParameter startParameter) {
        this.workerFactory = workerFactory;
        this.startParameter = startParameter;
    }

    public CompilerDaemonClient startDaemon(File workingDir, DaemonForkOptions forkOptions) {
        LOG.debug("Starting Gradle compiler daemon with fork options {}.", forkOptions);
        Clock clock = new Clock();
        WorkerProcessBuilder builder = workerFactory.create();
        builder.setLogLevel(startParameter.getLogLevel()); // NOTE: might make sense to respect per-compile-task log level
        builder.applicationClasspath(forkOptions.getClasspath());
        builder.sharedPackages(forkOptions.getSharedPackages());
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setMinHeapSize(forkOptions.getMinHeapSize());
        javaCommand.setMaxHeapSize(forkOptions.getMaxHeapSize());
        javaCommand.setJvmArgs(forkOptions.getJvmArgs());
        javaCommand.setWorkingDir(workingDir);
        WorkerProcess process = builder.worker(new CompilerDaemonServer()).setBaseName("Gradle Compiler Daemon").build();
        process.start();

        CompilerDaemonServerProtocol server = process.getConnection().addOutgoing(CompilerDaemonServerProtocol.class);
        CompilerDaemonClient client = new CompilerDaemonClient(forkOptions, process, server);
        process.getConnection().addIncoming(CompilerDaemonClientProtocol.class, client);
        process.getConnection().connect();

        LOG.info("Started Gradle compiler daemon ({}) with fork options {}.", clock.getTime(), forkOptions);

        return client;
    }
}
