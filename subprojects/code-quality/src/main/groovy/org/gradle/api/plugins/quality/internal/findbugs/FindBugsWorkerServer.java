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

import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.Serializable;

public class FindBugsWorkerServer implements Action<WorkerProcessContext>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(FindBugsWorkerServer.class);
    private FindBugsSpec spec;

    public FindBugsWorkerServer(FindBugsSpec spec) {
        this.spec = spec;
    }

    public void execute(WorkerProcessContext context) {
        final FindBugsResult result = execute();
        final FindBugsWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(FindBugsWorkerClientProtocol.class);
        context.getServerConnection().connect();
        clientProtocol.executed(result);
    }

    public FindBugsResult execute() {
        LOGGER.debug("Executing FindBugs worker.");
        try {
            FindBugsExecuter findBugsExecuter = new FindBugsExecuter();
            return findBugsExecuter.runFindbugs(spec);
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while running FindBugs.", e);
            return new FindBugsResult(0, 0, 1, e); //mark result with error count 1
        }
    }
}
