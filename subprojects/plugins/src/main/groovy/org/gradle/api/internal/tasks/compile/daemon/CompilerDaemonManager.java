/*
 * Copyright 2011 the original author or authors.
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

import net.jcip.annotations.NotThreadSafe;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls the lifecycle of the compiler daemon and provides access to it.
 */
@NotThreadSafe
public class CompilerDaemonManager implements CompilerDaemonFactory {
    private static final Logger LOGGER = Logging.getLogger(CompilerDaemonManager.class);
    private static final CompilerDaemonManager INSTANCE = new CompilerDaemonManager();
    
    private final List<CompilerDaemonClient> clients = new ArrayList<CompilerDaemonClient>();
    private boolean firstRequest = true;

    public static CompilerDaemonManager getInstance() {
        return INSTANCE;
    }

    public synchronized CompilerDaemon getDaemon(ProjectInternal project, DaemonForkOptions forkOptions) {
        if (firstRequest) {
            registerStopOnBuildFinished(project);
            firstRequest = false;
        }

        for (CompilerDaemonClient client: clients) {
            if (client.isCompatibleWith(forkOptions)) {
                return client;
            }
        }

        CompilerDaemonClient client = startDaemon(project, forkOptions);
        clients.add(client);
        return client;
    }

    public synchronized void stop() {
        LOGGER.info("Stopping {} Gradle compiler daemon(s).", clients.size());
        for (CompilerDaemonClient client : clients) {
            client.stop();
        }
        LOGGER.info("Stopped {} Gradle compiler daemon(s).", clients.size());
    }

    private void registerStopOnBuildFinished(ProjectInternal project) {
        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                stop();
            }
        });
    }

    private CompilerDaemonClient startDaemon(ProjectInternal project, DaemonForkOptions forkOptions) {
        LOGGER.info("Starting Gradle compiler daemon with fork options {}.", forkOptions);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(forkOptions.toString());
        }

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

        LOGGER.info("Started Gradle compiler daemon with fork options {}.", forkOptions);

        return client;
    }
}
