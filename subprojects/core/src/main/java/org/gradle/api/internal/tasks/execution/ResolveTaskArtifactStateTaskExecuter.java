/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResolveTaskArtifactStateTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveTaskArtifactStateTaskExecuter.class);

    private final TaskExecuter executer;
    private final TaskArtifactStateRepository repository;

    public ResolveTaskArtifactStateTaskExecuter(TaskArtifactStateRepository repository, TaskExecuter executer) {
        this.executer = executer;
        this.repository = repository;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        Timer clock = Timers.startTimer();
        context.setTaskArtifactState(repository.getStateFor(task));
        LOGGER.info("Putting task artifact state for {} into context took {}.", task, clock.getElapsed());
        try {
            executer.execute(task, state, context);
        } finally {
            context.setTaskArtifactState(null);
            LOGGER.debug("Removed task artifact state for {} from context.");
        }
    }
}
