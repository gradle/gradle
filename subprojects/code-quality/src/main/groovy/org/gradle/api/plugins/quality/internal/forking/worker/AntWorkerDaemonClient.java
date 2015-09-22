/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.forking.worker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.plugins.quality.internal.forking.AntResult;
import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.process.internal.WorkerProcess;


public class AntWorkerDaemonClient implements AntWorkerDaemon, AntWorkerDaemonClientProtocol, Stoppable {
    private final DaemonForkOptions forkOptions;
    private final WorkerProcess workerProcess;
    private final AntWorkerDaemonServerProtocol server;
    private final BlockingQueue<AntResult> compileResults = new SynchronousQueue<AntResult>();

    public AntWorkerDaemonClient(DaemonForkOptions forkOptions, WorkerProcess workerProcess, AntWorkerDaemonServerProtocol server) {

        this.forkOptions = forkOptions;
        this.workerProcess = workerProcess;
        this.server = server;
    }

    @Override
    public <T extends AntWorkerSpec> AntResult execute(T spec) {
        // one problem to solve when allowing multiple threads is how to deal with memory requirements specified by ant tasks
        try {
            server.executeSpec(spec);
            return compileResults.take();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean isCompatibleWith(DaemonForkOptions required) {
        return forkOptions.isCompatibleWith(required);
    }

    @Override
    public void executed(AntResult result) {
        try {
            compileResults.put(result);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void stop() {
        server.stop();
        workerProcess.waitForStop();
    }
}
