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

package org.gradle.internal.build;

import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.plan.ExecutionPlan;

import java.util.function.Consumer;

public interface BuildWorkPreparer {
    /**
     * Creates a new, empty plan.
     */
    ExecutionPlan newExecutionPlan();

    /**
     * Populates the given execution plan using the given action.
     */
    void populateWorkGraph(GradleInternal gradle, ExecutionPlan plan, Consumer<? super ExecutionPlan> action);

    /**
     * Finalises the given execution plan once all work has been scheduled.
     */
    void finalizeWorkGraph(GradleInternal gradle, ExecutionPlan plan);
}
