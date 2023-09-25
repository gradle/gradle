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

package org.gradle.initialization;

import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.ExecutionPlan;

import javax.annotation.Nullable;

/**
 * Responsible for preparing `Gradle` instances for task execution. The result is passed to a {@link org.gradle.execution.BuildWorkExecutor} for execution. Prior to preparing for task execution, the `Gradle` instance has its projects configured by a {@link org.gradle.configuration.ProjectsPreparer}.
 *
 * <p>This includes resolving the entry tasks and calculating the task graph.</p>
 */
public interface TaskExecutionPreparer {
    void scheduleRequestedTasks(GradleInternal gradle, @Nullable EntryTaskSelector selector, ExecutionPlan plan, boolean isModelBuildingRequested);
}
