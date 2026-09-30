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

package org.gradle.api.internal.provider;

import org.gradle.api.Task;
import org.gradle.internal.evaluation.EvaluationContext;
import org.gradle.internal.evaluation.EvaluationOwner;
import org.jspecify.annotations.Nullable;

/**
 * An evaluation whose result is reached through a task.
 * <p>
 * An output property of an object that is nested in a task does not always know which task declares it.
 * While the evaluation is in progress, such a property uses the task of the evaluation as its producer.
 * </p>
 */
interface TaskContextScope extends EvaluationOwner {
    /**
     * Returns the task that the result of this evaluation is reached through, if any.
     */
    @Nullable
    Task getTaskContext();

    /**
     * Returns the task of the innermost evaluation in progress that has one.
     */
    @Nullable
    static Task findTaskContext() {
        return EvaluationContext.current().findInScope(owner -> owner instanceof TaskContextScope ? ((TaskContextScope) owner).getTaskContext() : null);
    }
}
