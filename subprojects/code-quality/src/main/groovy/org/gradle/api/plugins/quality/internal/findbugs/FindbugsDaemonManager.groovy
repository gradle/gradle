/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.findbugs

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.process.internal.JavaExecHandleBuilder
import org.gradle.process.internal.WorkerProcess
import org.gradle.process.internal.WorkerProcessBuilder

class FindBugsDaemonManager {
    private static final Logger LOGGER = Logging.getLogger(FindBugsDaemonManager.class)

    private static final FindBugsDaemonManager INSTANCE = new FindBugsDaemonManager();

    private volatile FindBugsDaemon client;
    private volatile WorkerProcess process;

    public static FindBugsDaemonManager getInstance() {
        return INSTANCE;
    }

    public FindBugsDaemon getDaemon(ProjectInternal project, findBugsClasspath) {
        if (client != null) {
            stop();
        }
        if (client == null) {
            startDaemon(project, findBugsClasspath);
            stopDaemonOnceBuildFinished(project);
        }
        return client;
    }

    public void stop() {
        if (client == null) {
            return;
        }

        LOGGER.info("Stopping Gradle findbugs daemon.");

        client.stop();
        client = null;
        process.waitForStop();
        process = null;

        LOGGER.info("Gradle findbugs daemon stopped.");
    }

    private void startDaemon(ProjectInternal project, FileCollection findBugsClasspath) {
        LOGGER.info("Starting Gradle findbugs daemon.");
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(findBugsClasspath);
        }

        WorkerProcessBuilder builder = project.getServices().getFactory(WorkerProcessBuilder.class).create();
        builder.setLogLevel(project.getGradle().getStartParameter().getLogLevel());
        builder.applicationClasspath(findBugsClasspath);   //findbugs classpath
        builder.sharedPackages(Arrays.asList("edu.umd.cs.findbugs"));

        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(project.getRootProject().getProjectDir());
        process = builder.worker(new FindBugsDaemonServer()).build();
        process.start();
        FindBugsDaemonServerProtocol server = process.getConnection().addOutgoing(FindBugsDaemonServerProtocol.class);
        client = new FindBugsDaemonClient(server);
        process.getConnection().addIncoming(FindBugsDaemonClientProtocol.class, client);

        LOGGER.info("Gradle findbugs daemon started.");
    }

    private void stopDaemonOnceBuildFinished(ProjectInternal project) {
        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                stop();
            }
        });
    }
}