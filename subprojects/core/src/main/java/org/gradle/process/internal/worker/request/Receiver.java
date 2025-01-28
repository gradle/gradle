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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.ProblemTaskPathTracker;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.dispatch.StreamCompletion;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.remote.internal.hub.StreamFailureHandler;
import org.gradle.process.internal.worker.DefaultWorkerLoggingProtocol;
import org.gradle.process.internal.worker.DefaultWorkerProblemProtocol;
import org.gradle.process.internal.worker.WorkerProcessException;
import org.gradle.process.internal.worker.child.WorkerLoggingProtocol;
import org.gradle.process.internal.worker.problem.WorkerProblemProtocol;

import javax.annotation.Nullable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Receives and handles messages about a given worker action executed by a worker process.
 * <p>
 * This receiver is used per worker action.
 */
@NonNullApi
public class Receiver implements ResponseProtocol, StreamCompletion, StreamFailureHandler {
    private static final Object NULL = new Object();
    private static final Object END = new Object();
    private final BlockingQueue<Object> received = new ArrayBlockingQueue<Object>(10);
    private final String baseName;
    private Object next;
    private final String taskPath;

    // Sub-handlers for the different protocols implemented by ResponseProtocol
    private final WorkerLoggingProtocol loggingProtocol;
    private final WorkerProblemProtocol problemProtocol;

    public Receiver(String baseName, OutputEventListener outputEventListener) {
        this.loggingProtocol = new DefaultWorkerLoggingProtocol(outputEventListener);
        this.problemProtocol = new DefaultWorkerProblemProtocol();
        this.baseName = baseName;
        this.taskPath = ProblemTaskPathTracker.getTaskIdentityPath();
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

    @Nullable
    public Object getNextResult() {
        awaitNextResult();
        Object next = this.next;
        if (next == END) {
            throw new IllegalStateException("No response received.");
        }
        this.next = null;
        if (next instanceof Failure) {
            Failure failure = (Failure) next;
            throw UncheckedException.throwAsUncheckedException(failure.failure);
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
    public void completed(@Nullable Object result) {
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

    @Override
    public void reportProblem(Problem problem, OperationIdentifier id) {
        problem = this.taskPath == null ? problem : ((InternalProblem) problem).toBuilder(null, null, null).taskPathLocation(this.taskPath).build();
        problemProtocol.reportProblem(problem, id);
    }

    @Override
    public void sendOutputEvent(LogEvent event) {
        loggingProtocol.sendOutputEvent(event);
    }

    @Override
    public void sendOutputEvent(StyledTextOutputEvent event) {
        loggingProtocol.sendOutputEvent(event);
    }

    static class Failure {
        final Throwable failure;

        public Failure(Throwable failure) {
            this.failure = failure;
        }
    }
}
