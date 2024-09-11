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

import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.changes.InputChangesInternal;

import javax.annotation.Nullable;
import java.util.Optional;

public class InputChangesContext extends ValidationFinishedContext implements CachingContext {

    private final InputChangesInternal inputChanges;
    private final CachingState cachingState;

    public InputChangesContext(ValidationFinishedContext parent, @Nullable InputChangesInternal inputChanges, CachingState cachingState) {
        super(parent);
        this.inputChanges = inputChanges;
        this.cachingState = cachingState;
    }

    protected InputChangesContext(InputChangesContext parent) {
        this(parent, parent.getInputChanges().orElse(null), parent.getCachingState());
    }

    public Optional<InputChangesInternal> getInputChanges() {
        return Optional.ofNullable(inputChanges);
    }

    public boolean isIncrementalExecution() {
        return inputChanges != null && inputChanges.isIncremental();
    }

    @Override
    public CachingState getCachingState() {
        return cachingState;
    }
}
