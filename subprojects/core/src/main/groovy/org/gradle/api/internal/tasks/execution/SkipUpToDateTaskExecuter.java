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
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TaskExecuter} which skips tasks whose outputs are up-to-date.
 */
public class SkipUpToDateTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipUpToDateTaskExecuter.class);
    private final TaskExecuter executer;
    private final TaskArtifactStateRepository repository;

    public SkipUpToDateTaskExecuter(TaskExecuter executer, TaskArtifactStateRepository repository) {
        this.executer = executer;
        this.repository = repository;
    }

    public void execute(TaskInternal task, TaskStateInternal state) {
        LOGGER.debug("Determining if {} is up-to-date", task);
        TaskArtifactState taskArtifactState = repository.getStateFor(task);
        try {
            if (taskArtifactState.isUpToDate()) {
                LOGGER.info("Skipping {} as it is up-to-date", task);
                state.upToDate();
                return;

            }
            LOGGER.debug("{} is not up-to-date", task);

            taskArtifactState.beforeTask();
            task.getOutputs().setHistory(taskArtifactState.getExecutionHistory());

            // TODO:DAZ This is a crappy place for this
            if (task.isIncrementalTask()) {
                task.getOutputs().setTaskArtifactState(taskArtifactState);
            }
            try {
                executer.execute(task, state);
                if (state.getFailure() == null) {
                    taskArtifactState.afterTask();
                }
            } finally {
                task.getOutputs().setHistory(null);
            }
        } finally {
            taskArtifactState.finished();
        }
    }
}
