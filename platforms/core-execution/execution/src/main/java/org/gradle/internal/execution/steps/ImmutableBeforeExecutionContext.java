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
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;

import java.util.Optional;

public class ImmutableBeforeExecutionContext extends WorkspaceContext implements BeforeExecutionContext {
    private final BeforeExecutionState beforeExecutionState;

    public ImmutableBeforeExecutionContext(
        WorkspaceContext parent,
        BeforeExecutionState beforeExecutionState
    ) {
        super(parent);
        this.beforeExecutionState = beforeExecutionState;
    }

    protected ImmutableBeforeExecutionContext(ImmutableBeforeExecutionContext parent) {
        this(parent, parent.beforeExecutionState);
    }

    /**
     * Returns the execution state before execution.
     * Empty if execution state was not observed before execution.
     */
    @Override
    public Optional<BeforeExecutionState> getBeforeExecutionState() {
        return Optional.of(beforeExecutionState);
    }

    @Override
    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return beforeExecutionState.getInputProperties();
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
        return beforeExecutionState.getInputFileProperties();
    }
}
