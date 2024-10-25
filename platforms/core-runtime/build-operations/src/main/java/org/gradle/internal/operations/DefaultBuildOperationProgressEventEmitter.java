/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Timestamp;

import javax.annotation.Nullable;

public class DefaultBuildOperationProgressEventEmitter implements BuildOperationProgressEventEmitter {

    private final Clock clock;
    private final CurrentBuildOperationRef current;
    private final BuildOperationListener listener;

    public DefaultBuildOperationProgressEventEmitter(Clock clock, CurrentBuildOperationRef current, BuildOperationListener listener) {
        this.clock = clock;
        this.current = current;
        this.listener = listener;
    }

    @Override
    public void emit(@Nullable OperationIdentifier operationIdentifier, Timestamp time, @Nullable Object details) {
        // Explicit check in case of unsafe CurrentBuildOperationRef usage
        if (operationIdentifier == null) {
            throw new IllegalArgumentException("operationIdentifier is null");
        }
        doEmit(operationIdentifier, time, details);
    }

    @Override
    public void emitNow(@Nullable OperationIdentifier operationIdentifier, @Nullable Object details) {
        emit(operationIdentifier, clock.getTimestamp(), details);
    }

    @Override
    public void emitNowIfCurrent(Object details) {
        emitIfCurrent(clock.getTimestamp(), details);
    }

    private void emitIfCurrent(Timestamp time, Object details) {
        OperationIdentifier currentOperationIdentifier = current.getId();
        if (currentOperationIdentifier != null) {
            doEmit(currentOperationIdentifier, time, details);
        }
    }

    @Override
    public void emitNowForCurrent(Object details) {
        emitForCurrent(clock.getTimestamp(), details);
    }

    private void emitForCurrent(Timestamp time, Object details) {
        OperationIdentifier currentOperationIdentifier = current.getId();
        if (currentOperationIdentifier == null) {
            throw new IllegalStateException("No current build operation");
        } else {
            doEmit(currentOperationIdentifier, time, details);
        }
    }

    private void doEmit(OperationIdentifier operationIdentifier, Timestamp timestamp, @Nullable Object details) {
        listener.progress(operationIdentifier, new OperationProgressEvent(timestamp, details));
    }
}
