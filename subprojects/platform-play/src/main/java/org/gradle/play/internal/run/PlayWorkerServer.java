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

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class PlayWorkerServer implements Action<WorkerProcessContext>, PlayRunWorkerServerProtocol, Serializable {
    private PlayRunSpec spec;

    private volatile CountDownLatch stop;

    public PlayWorkerServer(PlayRunSpec spec) {
        this.spec = spec;
    }

    public void execute(WorkerProcessContext context) {
        stop = new CountDownLatch(1);
        final PlayRunWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(PlayRunWorkerClientProtocol.class);
        context.getServerConnection().addIncoming(PlayRunWorkerServerProtocol.class, this);
        context.getServerConnection().connect();
        final PlayAppLifecycleUpdate result = execute();
        try {
            stop.await();
            clientProtocol.executed(result);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public PlayAppLifecycleUpdate execute() {
        try {
            PlayExecuter playExcutor = new PlayExecuter();
            playExcutor.run(spec);
            return new PlayAppLifecycleUpdate(true);
        } catch (Exception e) {
            return new PlayAppLifecycleUpdate(e);
        }
    }

    public void stop() {
        System.out.println("Stopping the play application...");
        stop.countDown();
    }

}
