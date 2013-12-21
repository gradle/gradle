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

import org.gradle.internal.UncheckedException;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorker;
import org.mozilla.javascript.RhinoException;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class RhinoWorkerReceiver<P extends Serializable> implements RhinoClientWorkerProtocol<P> {

    private final Class<P> payloadType;
    private final RhinoWorker<?, P> worker;
    private final RhinoWorkerClientProtocol clientHandle;

    private final CountDownLatch latch = new CountDownLatch(1);

    public RhinoWorkerReceiver(Class<P> payloadType, RhinoWorkerClientProtocol clientHandle, RhinoWorker<?, P> worker) {
        this.payloadType = payloadType;
        this.clientHandle = clientHandle;
        this.worker = worker;
    }

    public void process(P payload) {
        if (!payloadType.isInstance(payload)) {
            clientHandle.initialisationError(
                    new IllegalArgumentException(String.format("Expected payload of type '%s', received '%s' with type '%s'", payloadType.getName(), payload, payload.getClass().getName()))
            );
            return;
        }

        try {
            Serializable result = worker.process(payload);
            clientHandle.receiveResult(result);
        } catch (RhinoException e) {
            clientHandle.executionError(worker.convertException(e));
        } catch (Exception e) {
            clientHandle.executionError(e);
        } finally {
            latch.countDown();
        }
    }

    public void waitFor() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
