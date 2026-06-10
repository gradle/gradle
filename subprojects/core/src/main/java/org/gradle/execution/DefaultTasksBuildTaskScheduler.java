/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.execution;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.internal.RunDefaultTasksExecutionRequest;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link BuildTaskScheduler} that selects the default tasks for a project, or if none are defined, the 'help' task.
 */
public class DefaultTasksBuildTaskScheduler implements BuildTaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTasksBuildTaskScheduler.class);
    private final List<BuiltInCommand> builtInCommands;
    private final BuildTaskScheduler delegate;

    public DefaultTasksBuildTaskScheduler(List<BuiltInCommand> builtInCommands, BuildTaskScheduler delegate) {
        this.builtInCommands = builtInCommands;
        this.delegate = delegate;
    }

    @Override
    public void scheduleRequestedTasks(GradleInternal gradle, List<TaskExecutionRequest> taskRequests, @Nullable EntryTaskSelector selector, ExecutionPlan plan) {
        if (taskRequests.size() == 1 && taskRequests.get(0) instanceof RunDefaultTasksExecutionRequest) {
            // Gather the default tasks from this first group project
            ProjectState defaultProjectState = gradle.getDefaultProjectState();
            // so that we don't miss out default tasks
            defaultProjectState.ensureConfigured();
            List<String> defaultTasks = defaultProjectState.fromMutableState(ProjectInternal::getDefaultTasks);
            if (defaultTasks.isEmpty()) {
                defaultTasks = new ArrayList<>();
                for (BuiltInCommand command : builtInCommands) {
                    defaultTasks.addAll(command.asDefaultTask());
                }
                LOGGER.info("No tasks specified. Using default task {}", GUtil.toString(defaultTasks));
            } else {
                LOGGER.info("No tasks specified. Using project default tasks {}", GUtil.toString(defaultTasks));
            }

            // Pass the resolved tasks on to the delegate instead of writing them back into the shared
            // StartParameter: mutating it after settings evaluation is reported as an IP violation.
            taskRequests = Collections.singletonList(DefaultTaskExecutionRequest.of(defaultTasks));
        }

        delegate.scheduleRequestedTasks(gradle, taskRequests, selector, plan);
    }
}
