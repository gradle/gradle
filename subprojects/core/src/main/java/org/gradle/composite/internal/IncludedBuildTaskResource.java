/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.execution.plan.Node;

import java.util.function.Consumer;

/**
 * A resource produced by a task in an included build.
 */
public interface IncludedBuildTaskResource {

    enum State {
        NotScheduled(true), Scheduled(false), Success(true), Failed(true);

        private final boolean complete;

        State(boolean complete) {
            this.complete = complete;
        }

        public boolean isComplete() {
            return complete;
        }
    }

    /**
     * Queues the task for execution, but does not schedule it. Use {@link org.gradle.internal.buildtree.BuildTreeWorkGraph#scheduleWork(Consumer)} to schedule queued tasks.
     */
    void queueForExecution();

    /**
     * Invokes the given action when this task completes (as per {@link Node#isComplete()}). Does nothing if this task has already completed.
     */
    void onComplete(Runnable action);

    TaskInternal getTask();

    State getTaskState();

    String healthDiagnostics();
}
