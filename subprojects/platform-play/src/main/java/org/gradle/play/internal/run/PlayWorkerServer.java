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
import org.gradle.process.internal.WorkerProcessContext;

import java.io.Serializable;
import java.lang.management.ManagementFactory;

public class PlayWorkerServer implements Action<WorkerProcessContext>, Serializable {
    private PlayRunSpec spec;

    public PlayWorkerServer(PlayRunSpec spec) {
        this.spec = spec;
    }

    public void execute(WorkerProcessContext context) {
        final PlayRunWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(PlayRunWorkerClientProtocol.class);
        context.getServerConnection().connect();
        final PlayRunResult result = execute(clientProtocol);
        clientProtocol.executed(result);
    }

    public PlayRunResult execute(PlayRunWorkerClientProtocol clientProtocol) {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            clientProtocol.updateStatus(name);
            PlayExecuter playExcutor = new PlayExecuter();
            return playExcutor.run(spec);
        } catch (Exception e) {
            return new PlayRunResult();
        }
    }
}
