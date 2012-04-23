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
import org.gradle.process.internal.JavaExecHandleBuilder
import org.gradle.process.internal.WorkerProcess
import org.gradle.process.internal.WorkerProcessBuilder

class FindBugsWorkerManager {
    public FindBugsResult runWorker(ProjectInternal project, FileCollection findBugsClasspath, FindBugsSpec spec) {
        WorkerProcess process = createWorkerProcess(project, findBugsClasspath, spec);
        process.start();

        FindBugsWorkerClient clientCallBack = new FindBugsWorkerClient()
        process.connection.addIncoming(FindBugsWorkerClientProtocol.class, clientCallBack);
        FindBugsResult result = clientCallBack.getResult();

        process.waitForStop();
        return result;
    }

    private WorkerProcess createWorkerProcess(ProjectInternal project, FileCollection findBugsClasspath, FindBugsSpec spec) {
        WorkerProcessBuilder builder = project.getServices().getFactory(WorkerProcessBuilder.class).create();
        builder.setLogLevel(project.getGradle().getStartParameter().getLogLevel());
        builder.applicationClasspath(findBugsClasspath);   //findbugs classpath
        builder.sharedPackages(Arrays.asList("edu.umd.cs.findbugs"));
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(project.getRootProject().getProjectDir());

        WorkerProcess process = builder.worker(new FindBugsWorkerServer(spec)).build()
        return process
    }
}