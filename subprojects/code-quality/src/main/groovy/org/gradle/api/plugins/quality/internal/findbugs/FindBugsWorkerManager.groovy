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
import org.gradle.internal.Factory
import org.gradle.process.internal.JavaExecHandleBuilder
import org.gradle.process.internal.WorkerProcess
import org.gradle.process.internal.WorkerProcessBuilder

class FindBugsWorkerManager {
    public FindBugsResult runWorker(File workingDir, Factory<WorkerProcessBuilder> workerFactory, FileCollection findBugsClasspath, FindBugsSpec spec) {
        WorkerProcess process = createWorkerProcess(workingDir, workerFactory, findBugsClasspath, spec);
        process.start();

        FindBugsWorkerClient clientCallBack = new FindBugsWorkerClient()
        process.connection.addIncoming(FindBugsWorkerClientProtocol.class, clientCallBack);
        process.connection.connect()

        FindBugsResult result = clientCallBack.getResult();

        process.waitForStop();
        return result;
    }

    private WorkerProcess createWorkerProcess(File workingDir, Factory<WorkerProcessBuilder> workerFactory, FileCollection findBugsClasspath, FindBugsSpec spec) {
        WorkerProcessBuilder builder = workerFactory.create();
        builder.setBaseName("Gradle FindBugs Worker")
        builder.applicationClasspath(findBugsClasspath);
        builder.sharedPackages(Arrays.asList("edu.umd.cs.findbugs"));
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(workingDir);
        javaCommand.setMaxHeapSize(spec.getMaxHeapSize());

        WorkerProcess process = builder.worker(new FindBugsWorkerServer(spec)).build()
        return process
    }
}