/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.internal.InternalListener;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;
import org.jspecify.annotations.NullMarked;

/**
 * Allows extra events about task graph execution to be received.
 */
@EventScope(Scope.Build.class)
@NullMarked
public interface TaskExecutionGraphExecutionListener extends InternalListener {
    /**
     * Called immediately before the graph starts executing tasks.
     * No execution-related changes have been made to the graph yet.
     *
     * @param graph the graph to be executed
     */
    void beforeGraphExecutionStarts(TaskExecutionGraphInternal graph);
}
