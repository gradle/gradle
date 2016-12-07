/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker.request;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.dispatch.StreamCompletion;
import org.gradle.internal.remote.internal.hub.StreamFailureHandler;
import org.gradle.process.internal.worker.WorkerProcessException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Receiver implements ResponseProtocol, StreamCompletion, StreamFailureHandler {
    private static final Object NULL = new Object();
    private static final Object END = new Object();
    private final BlockingQueue<Object> received = new ArrayBlockingQueue<Object>(10);
    private final String baseName;
    private Object next;

    public Receiver(String baseName) {
        this.baseName = baseName;
    }

    public boolean awaitNextResult() {
        try {
            if (next == null) {
                next = received.take();
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return next != END;
    }

    public Object getNextResult() throws Throwable {
        awaitNextResult();
        Object next = this.next;
        if (next == END) {
            throw new IllegalStateException("No response received.");
        }
        this.next = null;
        if (next instanceof Failure) {
            Failure failure = (Failure) next;
            throw failure.failure;
        }
        return next == NULL ? null : next;
    }

    @Override
    public void handleStreamFailure(Throwable t) {
        failed(t);
    }

    @Override
    public void endStream() {
        try {
            received.put(END);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void completed(Object result) {
        try {
            received.put(result == null ? NULL : result);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void infrastructureFailed(Throwable failure) {
        failed(WorkerProcessException.runFailed(baseName, failure));
    }

    @Override
    public void failed(Throwable failure) {
        try {
            received.put(new Failure(failure));
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    static class Failure {
        final Throwable failure;

        public Failure(Throwable failure) {
            this.failure = failure;
        }
    }
}
