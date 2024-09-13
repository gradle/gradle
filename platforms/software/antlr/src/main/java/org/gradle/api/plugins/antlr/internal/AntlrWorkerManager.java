/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.antlr.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.RequestHandler;
import org.gradle.process.internal.worker.SingleRequestWorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;

public class AntlrWorkerManager {

    public AntlrResult runWorker(File workingDir, WorkerProcessFactory workerFactory, FileCollection antlrClasspath, AntlrSpec spec) {

        RequestHandler<AntlrSpec, AntlrResult> antlrWorker = createWorkerProcess(workingDir, workerFactory, antlrClasspath, spec);
        return antlrWorker.run(spec);
    }

    private RequestHandler<AntlrSpec, AntlrResult> createWorkerProcess(File workingDir, WorkerProcessFactory workerFactory, FileCollection antlrClasspath, AntlrSpec spec) {
        SingleRequestWorkerProcessBuilder<AntlrSpec, AntlrResult> builder = workerFactory.singleRequestWorker(AntlrExecuter.class);
        builder.setBaseName("Gradle ANTLR Worker");

        if (antlrClasspath != null) {
            builder.applicationClasspath(antlrClasspath);
        }
        builder.sharedPackages("antlr", "org.antlr");
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(workingDir);
        javaCommand.getMaxHeapSize().set(spec.getMaxHeapSize());
        javaCommand.systemProperty("ANTLR_DO_NOT_EXIT", "true");
        javaCommand.redirectErrorStream();
        return builder.build();
    }
}
