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
import org.gradle.plugins.javascript.rhino.worker.RhinoWorkerHandle;
import org.gradle.process.internal.WorkerProcess;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class DefaultRhinoWorkerHandle<R extends Serializable, P extends Serializable> implements RhinoWorkerHandle<R, P> {

    private final Class<R> resultType;
    private final WorkerProcess workerProcess;

    public DefaultRhinoWorkerHandle(Class<R> resultType, WorkerProcess workerProcess) {
        this.resultType = resultType;
        this.workerProcess = workerProcess;
    }

    public R process(P payload) {
        CountDownLatch latch = new CountDownLatch(1);
        Receiver receiver = new Receiver(latch);
        workerProcess.start();

        workerProcess.getConnection().addIncoming(RhinoWorkerClientProtocol.class, receiver);
        @SuppressWarnings("unchecked") RhinoClientWorkerProtocol<P> worker = workerProcess.getConnection().addOutgoing(RhinoClientWorkerProtocol.class);
        workerProcess.getConnection().connect();

        worker.process(payload);

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        workerProcess.waitForStop();

        if (receiver.initialisationError != null) {
            throw UncheckedException.throwAsUncheckedException(receiver.initialisationError);
        }
        if (receiver.executionError != null) {
            throw UncheckedException.throwAsUncheckedException(receiver.executionError);
        }

        Serializable result = receiver.result;
        if (result == null) {
            return null;
        }

        if (resultType.isInstance(result)) {
            return resultType.cast(result);
        } else {
            throw new IllegalStateException(String.format("Was expecting result of type %s, received %s", resultType, result.getClass()));
        }
    }

    private static class Receiver implements RhinoWorkerClientProtocol {

        private final CountDownLatch latch;
        Exception initialisationError;
        Serializable result;
        Exception executionError;

        private Receiver(CountDownLatch latch) {
            this.latch = latch;
        }

        public void initialisationError(Exception e) {
            this.initialisationError = e;
            latch.countDown();
        }

        public void receiveResult(Serializable result) {
            this.result = result;
            latch.countDown();
        }

        public void executionError(Exception e) {
            this.executionError = e;
            latch.countDown();
        }
    }
}
