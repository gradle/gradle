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

package org.gradle.plugins.javascript.rhino.worker;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.Serializable;

public class RhinoServer implements Action<WorkerProcessContext>, Serializable {

    private final Class<? extends Transformer<? extends Serializable, ? extends Serializable>> implementationClass;

    public RhinoServer(Class<? extends Transformer<? extends Serializable, ? extends Serializable>> implementationClass) {
        this.implementationClass = implementationClass;
    }

    public void execute(WorkerProcessContext context) {
        RhinoWorkerClientProtocol clientHandle = context.getServerConnection().addOutgoing(RhinoWorkerClientProtocol.class);

        Transformer<Serializable, Serializable> action;

        try {
            Class<?> actionClass = getClass().getClassLoader().loadClass(implementationClass.getName());
            Object actionObject = actionClass.newInstance();
            if (actionObject instanceof Transformer) {
                //noinspection unchecked
                action = (Transformer<Serializable, Serializable>) actionObject;
            } else {
                throw new IllegalStateException(String.format("Implementation class %s is not a transformer", implementationClass));
            }


        } catch (Exception e) {
            clientHandle.initialisationError(e);
            return;
        }

        RhinoWorkerReceiver receiver = new RhinoWorkerReceiver(clientHandle, action);
        context.getServerConnection().addIncoming(RhinoClientWorkerProtocol.class, receiver);
        receiver.waitFor();
    }


}
