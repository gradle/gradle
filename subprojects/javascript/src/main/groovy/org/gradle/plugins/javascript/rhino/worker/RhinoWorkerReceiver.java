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

import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class RhinoWorkerReceiver implements RhinoClientWorkerProtocol {

    private final Transformer<Serializable, Serializable> impl;
    private final RhinoWorkerClientProtocol clientHandle;

    private final CountDownLatch latch = new CountDownLatch(1);

    public RhinoWorkerReceiver(RhinoWorkerClientProtocol clientHandle, Transformer<Serializable, Serializable> impl) {
        this.clientHandle = clientHandle;
        this.impl = impl;
    }

    public void process(Serializable payload) {
        try {
            Serializable result = impl.transform(payload);
            clientHandle.receiveResult(result);
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
