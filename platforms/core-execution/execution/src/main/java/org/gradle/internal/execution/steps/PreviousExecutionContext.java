/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

import javax.annotation.Nullable;
import java.util.Optional;

public class PreviousExecutionContext extends WorkspaceContext {
    private final PreviousExecutionState previousExecutionState;

    public PreviousExecutionContext(WorkspaceContext parent, @Nullable PreviousExecutionState previousExecutionState) {
        super(parent);
        this.previousExecutionState = previousExecutionState;
    }

    public PreviousExecutionContext withInputFiles(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles) {
        return new PreviousExecutionContext(this) {
            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return inputFiles;
            }
        };
    }

    protected PreviousExecutionContext(PreviousExecutionContext parent) {
        this(parent, parent.getPreviousExecutionState().orElse(null));
    }

    /**
     * Returns the execution state after the previous execution if available.
     * Empty when execution history is not available.
     */
    public Optional<PreviousExecutionState> getPreviousExecutionState() {
        return Optional.ofNullable(previousExecutionState);
    }
}
