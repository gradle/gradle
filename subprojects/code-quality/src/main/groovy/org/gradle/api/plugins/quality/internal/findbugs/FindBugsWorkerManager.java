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

package org.gradle.api.plugins.quality.internal.findbugs;

import org.gradle.api.file.FileCollection;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.SingleRequestWorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class FindBugsWorkerManager {
    public FindBugsResult runWorker(File workingDir, WorkerProcessFactory workerFactory, FileCollection findBugsClasspath, FindBugsSpec spec) throws IOException, InterruptedException {
        FindBugsWorker worker = createWorkerProcess(workingDir, workerFactory, findBugsClasspath, spec);
        return worker.runFindbugs(spec);
    }

    private FindBugsWorker createWorkerProcess(File workingDir, WorkerProcessFactory workerFactory, FileCollection findBugsClasspath, FindBugsSpec spec) {
        SingleRequestWorkerProcessBuilder<FindBugsWorker> builder = workerFactory.singleRequestWorker(FindBugsWorker.class, FindBugsExecuter.class);
        builder.setBaseName("Gradle FindBugs Worker");
        builder.applicationClasspath(findBugsClasspath);
        builder.sharedPackages(Arrays.asList("edu.umd.cs.findbugs"));
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(workingDir);
        javaCommand.setMaxHeapSize(spec.getMaxHeapSize());
        javaCommand.jvmArgs(spec.getJvmArgs());

        return builder.build();
    }
}
