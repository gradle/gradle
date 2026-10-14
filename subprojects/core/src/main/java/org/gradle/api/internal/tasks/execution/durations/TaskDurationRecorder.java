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

import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskState;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.service.scopes.ListenerService;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Clock;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the duration of each successfully executed task in the build tree.
 */
@ListenerService
@ServiceScope(Scope.BuildTree.class)
public class TaskDurationRecorder implements TaskListenerInternal {
    private final DefaultTaskDurationHistory history;
    private final Clock clock;
    private final Map<Long, Long> startTimes = new ConcurrentHashMap<>();

    public TaskDurationRecorder(DefaultTaskDurationHistory history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    @Override
    public void beforeExecute(TaskIdentity<?> taskIdentity) {
        startTimes.put(taskIdentity.getId(), clock.getCurrentTime());
    }

    @Override
    public void afterExecute(TaskIdentity<?> taskIdentity, TaskState state) {
        Long startTime = startTimes.remove(taskIdentity.getId());
        if (startTime == null || state.getFailure() != null) {
            return;
        }
        TaskExecutionOutcome outcome = ((TaskStateInternal) state).getOutcome();
        if (outcome == TaskExecutionOutcome.EXECUTED) {
            history.record(taskIdentity.getBuildTreePath(), Duration.ofMillis(clock.getCurrentTime() - startTime));
        } else if (outcome == TaskExecutionOutcome.NO_SOURCE) {
            // A task without sources does no work
            history.record(taskIdentity.getBuildTreePath(), Duration.ZERO);
        }
    }
}
