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

package org.gradle.process.internal.worker;

import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.process.internal.worker.child.WorkerLoggingProtocol;

import java.util.concurrent.Callable;

public class DefaultWorkerLoggingProtocol implements WorkerLoggingProtocol {
    private OutputEventListener outputEventListener;
    private final Callable<OperationIdentifier> operationId;

    public DefaultWorkerLoggingProtocol(OutputEventListener outputEventListener, Callable<OperationIdentifier> operationId) {
        this.outputEventListener = outputEventListener;
        this.operationId = operationId;
    }

    @Override
    public void sendOutputEvent(LogEvent event) {
        try {
            event.setOperationId(operationId.call());
        } catch (Exception e) {}
        outputEventListener.onOutput(event);
    }

    @Override
    public void sendOutputEvent(StyledTextOutputEvent event) {
        try {
            event.setOperationId(operationId.call());
        } catch (Exception e) {}
        outputEventListener.onOutput(event);
    }

    private OperationIdentifier getId() {
        OperationIdentifier operationId = new OperationIdentifier(-55);
        if (null != DefaultProgressLoggerFactory.instance) {
            operationId = new OperationIdentifier(-54);
            if (null != DefaultProgressLoggerFactory.instance.getCurrentProgressLogger()) {
                operationId = DefaultProgressLoggerFactory.instance.getCurrentProgressLogger().getId();
            }
        }
        return operationId;
    }
}
