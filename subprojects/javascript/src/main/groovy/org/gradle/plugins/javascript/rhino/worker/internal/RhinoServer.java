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

package org.gradle.plugins.javascript.rhino.worker.internal;

import org.gradle.api.Action;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorker;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerSpec;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.Serializable;

public class RhinoServer<R extends Serializable, P extends Serializable> implements Action<WorkerProcessContext>, Serializable {

    private final RhinoWorkerSpec<R, P> workerSpec;

    public RhinoServer(RhinoWorkerSpec<R, P> workerSpec) {
        this.workerSpec = workerSpec;
    }

    public void execute(WorkerProcessContext context) {
        RhinoWorkerClientProtocol clientHandle = context.getServerConnection().addOutgoing(RhinoWorkerClientProtocol.class);
        context.getServerConnection().connect();

        RhinoWorker<R, P> action;

        try {
            Class<?> actionClass = getClass().getClassLoader().loadClass(workerSpec.getWorkerType().getName());
            Object actionObject = actionClass.newInstance();
            if (actionObject instanceof RhinoWorker) {
                //noinspection unchecked
                action = (RhinoWorker<R, P>) actionObject;
            } else {
                throw new IllegalStateException(String.format("Implementation class %s is not a transformer", workerSpec.getWorkerType().getName()));
            }


        } catch (Exception e) {
            clientHandle.initialisationError(e);
            return;
        }

        RhinoWorkerReceiver receiver = new RhinoWorkerReceiver<P>(workerSpec.getPayloadType(), clientHandle, action);
        context.getServerConnection().addIncoming(RhinoClientWorkerProtocol.class, receiver);
        receiver.waitFor();
    }

}
