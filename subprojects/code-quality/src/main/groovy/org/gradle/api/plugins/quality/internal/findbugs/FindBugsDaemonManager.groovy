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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.process.internal.JavaExecHandleBuilder
import org.gradle.process.internal.WorkerProcess
import org.gradle.process.internal.WorkerProcessBuilder

class FindBugsDaemonManager {
    private final Logger logger = Logging.getLogger(getClass())

    public FindBugsResult runDaemon(ProjectInternal project, FileCollection findBugsClasspath, FindBugsSpec spec) {
        logger.info("Starting Gradle findbugs daemon.");
        if (logger.isDebugEnabled()) {
            logger.debug(findBugsClasspath.asPath);
        }

        WorkerProcess process = createWorkerProcess(project, findBugsClasspath, spec);
        process.start();

        FindBugsDaemonClient clientCallBack = new FindBugsDaemonClient()
        process.connection.addIncoming(FindBugsDaemonClientProtocol.class, clientCallBack);
        FindBugsResult result = clientCallBack.getResult();

        process.waitForStop();
        logger.info("Gradle findbugs daemon stopped.");
        return result;
    }

    private WorkerProcess createWorkerProcess(ProjectInternal project, FileCollection findBugsClasspath, FindBugsSpec spec) {
        WorkerProcessBuilder builder = project.getServices().getFactory(WorkerProcessBuilder.class).create();
        builder.setLogLevel(project.getGradle().getStartParameter().getLogLevel());
        builder.applicationClasspath(findBugsClasspath);   //findbugs classpath
        builder.sharedPackages(Arrays.asList("edu.umd.cs.findbugs"));
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(project.getRootProject().getProjectDir());

        WorkerProcess process = builder.worker(new FindBugsDaemonServer(spec)).build()
        return process
    }
}