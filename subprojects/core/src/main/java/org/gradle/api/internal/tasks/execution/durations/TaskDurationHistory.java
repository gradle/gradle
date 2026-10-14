/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution.durations;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

/**
 * Provides the durations of recent successful executions of tasks in previous builds of this build tree.
 */
@ServiceScope(Scope.BuildTree.class)
public interface TaskDurationHistory {
    /**
     * Returns the durations for the task with the given build tree path, or null when it has no recorded successful executions.
     */
    @Nullable
    TaskDurations getDurations(Path buildTreeTaskPath);
}
