/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Optional;

/**
 * Provides access to the work executing on the current thread.
 */
@ServiceScope(Scope.Global.class)
public interface WorkExecutionTracker {

    /**
     * Finds the task executing on the current thread, if any.
     */
    Optional<TaskInternal> getCurrentTask();

    /**
     * Finds the task executing the target operation, if any.
     */
    Optional<TaskInternal> getCurrentTask(OperationIdentifier id);

    /**
     * Checks if the current thread is executing a {@link org.gradle.api.artifacts.transform.TransformAction}.
     *
     * @return {@code true} if the current thread is executing a transform action
     */
    boolean isExecutingTransformAction();
}
