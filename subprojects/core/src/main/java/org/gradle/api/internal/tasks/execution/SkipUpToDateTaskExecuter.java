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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * A {@link TaskExecuter} which skips tasks whose outputs are up-to-date.
 */
public class SkipUpToDateTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipUpToDateTaskExecuter.class);
    private final TaskExecuter executer;

    public SkipUpToDateTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, final TaskExecutionContext context) {
        LOGGER.debug("Determining if {} is up-to-date", task);
        final TaskArtifactState taskArtifactState = context.getTaskArtifactState();

        List<String> messages = new ArrayList<String>(ExecutionStateChanges.MAX_OUT_OF_DATE_MESSAGES);
        if (taskArtifactState.isUpToDate(messages)) {
            LOGGER.info("Skipping {} as it is up-to-date.", task);
            state.setOutcome(TaskExecutionOutcome.UP_TO_DATE);
            return new TaskExecuterResult() {
                @Override
                public OriginMetadata getOriginMetadata() {
                    AfterPreviousExecutionState afterPreviousExecution = context.getAfterPreviousExecution();
                    return afterPreviousExecution == null
                        ? null
                        : afterPreviousExecution.getOriginMetadata();
                }
            };
        }
        context.setUpToDateMessages(ImmutableList.copyOf(messages));
        logOutOfDateMessages(messages, task);

        return executer.execute(task, state, context);
    }

    private void logOutOfDateMessages(List<String> messages, TaskInternal task) {
        if (LOGGER.isInfoEnabled()) {
            Formatter formatter = new Formatter();
            formatter.format("Task '%s' is not up-to-date because:", task.getIdentityPath());
            for (String message : messages) {
                formatter.format("%n  %s", message);
            }
            LOGGER.info(formatter.toString());
        }
    }
}
