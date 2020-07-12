/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;

/**
 * Specialised event emitter for cross cutting type progress events not tied more deeply to operation execution.
 */
@ServiceScope(Scopes.Global)
public class BuildOperationProgressEventEmitter {

    private final Clock clock;
    private final BuildOperationListener listener;
    private final CurrentBuildOperationRef current;
    private final RootBuildOperationRef root;

    public BuildOperationProgressEventEmitter(
        Clock clock,
        BuildOperationListener listener,
        CurrentBuildOperationRef current,
        RootBuildOperationRef root
    ) {
        this.clock = clock;
        this.listener = listener;
        this.current = current;
        this.root = root;
    }

    public void emit(OperationIdentifier operationIdentifier, long timestamp, @Nullable Object details) {
        // Explicit check in case of unsafe CurrentBuildOperationRef usage
        if (operationIdentifier == null) {
            throw new IllegalArgumentException("operationIdentifier is null");
        }
        doEmit(operationIdentifier, timestamp, details);
    }

    public void emitForCurrentOrRootOperationIfWithin(long time, Object details) {
        OperationIdentifier id = currentOrRootOperationId();
        if (id == null) {
            return;
        }

        doEmit(id, time, details);
    }

    public void emitNowForCurrentOrRootOperationIfWithin(Object details) {
        emitForCurrentOrRootOperationIfWithin(clock.getCurrentTime(), details);
    }

    public void emitNowForCurrentOperation(Object details) {
        emitForCurrentOperation(clock.getCurrentTime(), details);
    }

    private void emitForCurrentOperation(long time, Object details) {
        OperationIdentifier currentOperationIdentifier = current.getId();
        if (currentOperationIdentifier == null) {
            throw new IllegalStateException("No current build operation");
        } else {
            doEmit(currentOperationIdentifier, time, details);
        }
    }

    @Nullable
    private OperationIdentifier currentOrRootOperationId() {
        OperationIdentifier id = current.getId();
        return id == null ? root.maybeGetId() : id;
    }

    private void doEmit(OperationIdentifier operationIdentifier, long timestamp, @Nullable Object details) {
        listener.progress(operationIdentifier, new OperationProgressEvent(timestamp, details));
    }
}
