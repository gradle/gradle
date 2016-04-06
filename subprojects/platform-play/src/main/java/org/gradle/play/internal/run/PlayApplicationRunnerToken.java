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

import org.gradle.process.internal.worker.WorkerProcess;

import java.util.concurrent.atomic.AtomicBoolean;

public class PlayApplicationRunnerToken {

    private final PlayWorkerClient clientCallBack;
    private final PlayRunWorkerServerProtocol workerServer;
    private final WorkerProcess process;
    private final AtomicBoolean stopped;

    public PlayApplicationRunnerToken(PlayRunWorkerServerProtocol workerServer, PlayWorkerClient clientCallBack, WorkerProcess process) {
        this.workerServer = workerServer;
        this.clientCallBack = clientCallBack;
        this.process = process;
        this.stopped = new AtomicBoolean(false);
    }

    public PlayAppLifecycleUpdate stop() {
        workerServer.stop();
        PlayAppLifecycleUpdate update = clientCallBack.waitForStop();
        process.waitForStop();
        stopped.set(true);
        return update;
    }

    public void rebuildSuccess() {
        workerServer.reload();
    }

    public void rebuildFailure(Throwable failure) {
        workerServer.buildError(failure);
    }

    public boolean isRunning() {
        return !stopped.get();
    }
}
