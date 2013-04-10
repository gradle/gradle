/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.ContextualTaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ContextualTaskExecuter} which skips tasks whose outputs are up-to-date.
 */
public class SkipUpToDateTaskExecuter implements ContextualTaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipUpToDateTaskExecuter.class);
    private final ContextualTaskExecuter executer;
    private final TaskArtifactStateRepository repository;

    public SkipUpToDateTaskExecuter(TaskArtifactStateRepository repository, ContextualTaskExecuter executer) {
        this.executer = executer;
        this.repository = repository;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        LOGGER.debug("Determining if {} is up-to-date", task);
        TaskArtifactState taskArtifactState = repository.getStateFor(task);
        try {
            if (taskArtifactState.isUpToDate()) {
                LOGGER.info("Skipping {} as it is up-to-date", task);
                state.upToDate();
                return;

            }
            LOGGER.debug("{} is not up-to-date", task);

            task.getOutputs().setHistory(taskArtifactState.getExecutionHistory());
            context.setTaskArtifactState(taskArtifactState);

            taskArtifactState.beforeTask();
            try {
                executer.execute(task, state, context);
                if (state.getFailure() == null) {
                    taskArtifactState.afterTask();
                }
            } finally {
                task.getOutputs().setHistory(null);
                context.setTaskArtifactState(null);
            }
        } finally {
            taskArtifactState.finished();
        }
    }
}
