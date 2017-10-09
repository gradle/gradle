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
import org.gradle.api.internal.changedetection.rules.TaskUpToDateState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
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

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        LOGGER.debug("Determining if {} is up-to-date", task);
        Timer clock = Time.startTimer();
        TaskArtifactState taskArtifactState = context.getTaskArtifactState();

        List<String> messages = new ArrayList<String>(TaskUpToDateState.MAX_OUT_OF_DATE_MESSAGES);
        if (taskArtifactState.isUpToDate(messages)) {
            LOGGER.info("Skipping {} as it is up-to-date (took {}).", task, clock.getElapsed());
            state.setOutcome(TaskExecutionOutcome.UP_TO_DATE);
            context.setOriginBuildInvocationId(taskArtifactState.getOriginBuildInvocationId());
            return;
        }
        context.setUpToDateMessages(ImmutableList.copyOf(messages));
        logOutOfDateMessages(messages, task, clock.getElapsed());

        executer.execute(task, state, context);
    }

    private void logOutOfDateMessages(List<String> messages, TaskInternal task, String took) {
        if (LOGGER.isInfoEnabled()) {
            Formatter formatter = new Formatter();
            formatter.format("Up-to-date check for %s took %s. It is not up-to-date because:", task, took);
            for (String message : messages) {
                formatter.format("%n  %s", message);
            }
            LOGGER.info(formatter.toString());
        }
    }
}
