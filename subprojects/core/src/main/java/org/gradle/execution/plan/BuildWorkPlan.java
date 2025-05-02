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

package org.gradle.execution.plan;

import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.internal.concurrent.Stoppable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface BuildWorkPlan extends Stoppable {
    /**
     * Invokes the given action when a task completes (as per {@link Node#isComplete()}). Does nothing for tasks that have already completed.
     */
    void onComplete(Consumer<LocalTaskNode> handler);

    void addFilter(Spec<Task> filter);

    void addFinalization(BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan> finalization);
}
