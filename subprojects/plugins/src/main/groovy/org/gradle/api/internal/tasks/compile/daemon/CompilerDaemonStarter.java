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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.Clock;

import java.io.File;

class CompilerDaemonStarter {

    private static Logger LOG = Logging.getLogger(CompilerDaemonStarter.class);

    public CompilerDaemonClient startDaemon(ProjectInternal project, DaemonForkOptions forkOptions) {
        LOG.debug("Starting Gradle compiler daemon with fork options {}.", forkOptions);
        Clock clock = new Clock();
        WorkerProcessBuilder builder = project.getServices().getFactory(WorkerProcessBuilder.class).create();
        builder.setLogLevel(project.getGradle().getStartParameter().getLogLevel()); // NOTE: might make sense to respect per-compile-task log level
        builder.applicationClasspath(forkOptions.getClasspath());
        builder.sharedPackages(forkOptions.getSharedPackages());
        File toolsJar = Jvm.current().getToolsJar();
        if (toolsJar != null) {
            builder.getApplicationClasspath().add(toolsJar); // for SunJavaCompiler
        }
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setMinHeapSize(forkOptions.getMinHeapSize());
        javaCommand.setMaxHeapSize(forkOptions.getMaxHeapSize());
        javaCommand.setJvmArgs(forkOptions.getJvmArgs());
        javaCommand.setWorkingDir(project.getRootProject().getProjectDir());
        WorkerProcess process = builder.worker(new CompilerDaemonServer()).build();
        process.start();
        CompilerDaemonServerProtocol server = process.getConnection().addOutgoing(CompilerDaemonServerProtocol.class);
        CompilerDaemonClient client = new CompilerDaemonClient(forkOptions, process, server);
        process.getConnection().addIncoming(CompilerDaemonClientProtocol.class, client);

        LOG.info("Started Gradle compiler daemon ({}) with fork options {}.", clock.getTime(), forkOptions);

        return client;
    }
}
