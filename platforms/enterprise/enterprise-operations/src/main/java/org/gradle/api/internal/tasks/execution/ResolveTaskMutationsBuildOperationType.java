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

package org.gradle.api.internal.tasks.execution;

import org.gradle.internal.operations.BuildOperationType;

/**
 * Represents resolving the mutations of a task.
 * <p>
 * Before task execution ({@link org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType}),
 * the mutations of the task need to be resolved. The mutations of a task are the locations it may modify,
 * e.g. its outputs, destroyables and local state. In order to resolve the mutations, the task input and output
 * properties are detected as well. Since Gradle 7.5, resolving of the mutations happens in a separate node
 * in the execution graph.
 *
 * @since 7.6
 */
public final class ResolveTaskMutationsBuildOperationType implements BuildOperationType<ResolveTaskMutationsBuildOperationType.Details, ResolveTaskMutationsBuildOperationType.Result> {
    public interface Details {
        String getBuildPath();

        String getTaskPath();

        /**
         * See {@code org.gradle.api.internal.project.taskfactory.TaskIdentity#uniqueId}.
         */
        long getTaskId();
    }

    public interface Result {
    }
}
