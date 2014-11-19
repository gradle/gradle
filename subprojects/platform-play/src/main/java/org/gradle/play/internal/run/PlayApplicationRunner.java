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

package org.gradle.play.internal.run;

import org.gradle.api.GradleException;
import org.gradle.internal.Factory;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlayApplicationRunner {
    private final File workingDir;
    private final Factory<WorkerProcessBuilder> workerFactory;
    private final VersionedPlayRunSpec spec;
    private final Iterable<File>  docsClasspath;

    public PlayApplicationRunner(File workingDir, Factory<WorkerProcessBuilder> workerFactory, VersionedPlayRunSpec spec, Iterable<File> docsClasspath) {
        this.workingDir = workingDir;
        this.workerFactory = workerFactory;
        this.spec = spec;
        this.docsClasspath = docsClasspath;
    }

    public PlayApplicationRunnerToken start() {
        WorkerProcess process = createWorkerProcess(workingDir, workerFactory, docsClasspath, spec);
        process.start();

        PlayWorkerClient clientCallBack = new PlayWorkerClient();
        process.getConnection().addIncoming(PlayRunWorkerClientProtocol.class, clientCallBack);
        process.getConnection().connect();
        PlayAppLifecycleUpdate result = clientCallBack.waitForRunning();
        if(result.getStatus() == PlayAppStatus.RUNNING){
            return new PlayApplicationRunnerToken(clientCallBack);
        }else{
            throw new GradleException("Unable to start Play application.", result.getException());
        }
    }

    private static WorkerProcess createWorkerProcess(File workingDir, Factory<WorkerProcessBuilder> workerFactory, Iterable<File> docsClasspath, VersionedPlayRunSpec spec) {
        WorkerProcessBuilder builder = workerFactory.create();
        builder.setBaseName("Gradle Play Worker");
        //TODO freekh: we should try to avoid this, but we have to use the same classloader for docs also see PlayExecuter
        List<File> combinedClasspath = new ArrayList<File>();
        for (File file: docsClasspath) {
            combinedClasspath.add(file);
        }
        for (File file: spec.getClasspath()) {
            combinedClasspath.add(file);
        }
        builder.applicationClasspath(combinedClasspath);
        builder.sharedPackages(spec.getSharedPackages());
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(workingDir);
        javaCommand.setMinHeapSize(spec.getForkOptions().getMemoryInitialSize());
        javaCommand.setMaxHeapSize(spec.getForkOptions().getMemoryMaximumSize());
        WorkerProcess process = builder.worker(new PlayWorkerServer(spec, docsClasspath)).build();
        return process;
    }
}
