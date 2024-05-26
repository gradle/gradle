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

package org.gradle.process.internal.worker.child;

import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.StyledTextOutputEvent;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class WorkerLogEventListener implements OutputEventListener {
    private final AtomicReference<WorkerLoggingProtocol> workerLoggingProtocol;

    public WorkerLogEventListener() {
        this.workerLoggingProtocol = new AtomicReference<WorkerLoggingProtocol>();
    }

    public void setWorkerLoggingProtocol(WorkerLoggingProtocol workerLoggingProtocol) {
        this.workerLoggingProtocol.getAndSet(workerLoggingProtocol);
    }

    public Object withWorkerLoggingProtocol(WorkerLoggingProtocol newLoggingProtocol, Callable<?> callable) throws Exception {
        WorkerLoggingProtocol defaultProtocol = workerLoggingProtocol.getAndSet(newLoggingProtocol);
        try {
            return callable.call();
        } finally {
            workerLoggingProtocol.getAndSet(defaultProtocol);
        }
    }

    @Override
    public void onOutput(OutputEvent event) {
        WorkerLoggingProtocol loggingProtocol = workerLoggingProtocol.get();

        if (loggingProtocol == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " received an output event before the worker logging protocol object was set.");
        }

        if (event instanceof LogEvent) {
            loggingProtocol.sendOutputEvent((LogEvent) event);
        } else if (event instanceof StyledTextOutputEvent) {
            loggingProtocol.sendOutputEvent((StyledTextOutputEvent) event);
        }
    }
}
