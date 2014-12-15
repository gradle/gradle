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

public class PlayApplicationRunner {
    private final File workingDir;
    private final Factory<WorkerProcessBuilder> workerFactory;
    private final VersionedPlayRunSpec spec;

    public PlayApplicationRunner(File workingDir, Factory<WorkerProcessBuilder> workerFactory, VersionedPlayRunSpec spec) {
        this.workingDir = workingDir;
        this.workerFactory = workerFactory;
        this.spec = spec;
    }

    public PlayApplicationRunnerToken start() {
        WorkerProcess process = createWorkerProcess(workingDir, workerFactory, spec);
        process.start();

        PlayWorkerClient clientCallBack = new PlayWorkerClient();
        process.getConnection().addIncoming(PlayRunWorkerClientProtocol.class, clientCallBack);
        PlayRunWorkerServerProtocol workerServer = process.getConnection().addOutgoing(PlayRunWorkerServerProtocol.class);
        process.getConnection().connect();
        PlayAppLifecycleUpdate result = clientCallBack.waitForRunning();
        if (result.isRunning()) {
            return new PlayApplicationRunnerToken(workerServer, clientCallBack);
        } else {
            throw new GradleException("Unable to start Play application.", result.getException());
        }
    }

    private static WorkerProcess createWorkerProcess(File workingDir, Factory<WorkerProcessBuilder> workerFactory, VersionedPlayRunSpec spec) {
        WorkerProcessBuilder builder = workerFactory.create();
        builder.setBaseName("Gradle Play Worker");
        builder.applicationClasspath(spec.getClasspath());
        builder.sharedPackages(spec.getSharedPackages());
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(workingDir);
        javaCommand.setMinHeapSize(spec.getForkOptions().getMemoryInitialSize());
        javaCommand.setMaxHeapSize(spec.getForkOptions().getMemoryMaximumSize());
        return builder.worker(new PlayWorkerServer(spec)).build();
    }
}
