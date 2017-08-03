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
import org.gradle.initialization.BuildGateToken;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.WorkerProcess;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;

import java.io.File;

public class PlayApplicationRunner {
    private final WorkerProcessFactory workerFactory;
    private final BuildGateToken gateToken;
    private final VersionedPlayRunAdapter adapter;

    public PlayApplicationRunner(WorkerProcessFactory workerFactory, BuildGateToken gateToken, VersionedPlayRunAdapter adapter) {
        this.workerFactory = workerFactory;
        this.gateToken = gateToken;
        this.adapter = adapter;
    }

    public PlayApplicationRunnerToken start(PlayRunSpec spec) {
        WorkerProcess process = createWorkerProcess(spec.getProjectPath(), workerFactory, spec, adapter);
        process.start();

        PlayWorkerClient clientCallBack = new PlayWorkerClient(gateToken);
        process.getConnection().addIncoming(PlayRunWorkerClientProtocol.class, clientCallBack);
        PlayRunWorkerServerProtocol workerServer = process.getConnection().addOutgoing(PlayRunWorkerServerProtocol.class);
        process.getConnection().connect();
        PlayAppStart result = clientCallBack.waitForRunning();
        if (result.isRunning()) {
            return new PlayApplicationRunnerToken(workerServer, clientCallBack, process, result.getAddress());
        } else {
            throw new GradleException("Unable to start Play application.", result.getException());
        }
    }

    private static WorkerProcess createWorkerProcess(File workingDir, WorkerProcessFactory workerFactory, PlayRunSpec spec, VersionedPlayRunAdapter adapter) {
        WorkerProcessBuilder builder = workerFactory.create(new PlayWorkerServer(spec, adapter));
        builder.setBaseName("Gradle Play Worker");
        builder.sharedPackages("org.gradle.play.internal.run");
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(workingDir);
        javaCommand.setMinHeapSize(spec.getForkOptions().getMemoryInitialSize());
        javaCommand.setMaxHeapSize(spec.getForkOptions().getMemoryMaximumSize());
        javaCommand.setJvmArgs(spec.getForkOptions().getJvmArgs());
        return builder.build();
    }
}
