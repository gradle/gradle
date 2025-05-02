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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class IncrementalChangesContext extends ValidationFinishedContext {

    private final ImmutableList<String> rebuildReasons;
    private final ExecutionStateChanges executionStateChanges;

    public IncrementalChangesContext(
        ValidationFinishedContext parent,
        ImmutableList<String> rebuildReasons,
        @Nullable ExecutionStateChanges executionStateChanges
    ) {
        super(parent);
        this.rebuildReasons = rebuildReasons;
        this.executionStateChanges = executionStateChanges;
    }

    protected IncrementalChangesContext(IncrementalChangesContext parent) {
        this(parent, parent.getRebuildReasons(), parent.getChanges().orElse(null));
    }

    /**
     * Returns the reasons to re-execute the work, empty if there's no reason to re-execute.
     */
    public ImmutableList<String> getRebuildReasons() {
        return rebuildReasons;
    }

    /**
     * Returns changes detected between the execution state after the last execution and before the current execution.
     * Empty if changes couldn't be detected (e.g. because history was unavailable).
     */
    public Optional<ExecutionStateChanges> getChanges() {
        return Optional.ofNullable(executionStateChanges);
    }
}
