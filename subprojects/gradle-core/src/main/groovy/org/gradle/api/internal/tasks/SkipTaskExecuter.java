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

package org.gradle.api.internal.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class SkipTaskExecuter implements TaskExecuter {
    private static Logger logger = Logging.getLogger(SkipTaskExecuter.class);
    private final TaskExecuter executer;

    public SkipTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public void execute(TaskInternal task, TaskStateInternal state) {
        logger.debug("Starting to execute {}", task);
        try {
            doExecute(task, state);
        } finally {
            state.executed();
            logger.debug("Finished executing {}", task);
        }
    }

    private void doExecute(TaskInternal task, TaskStateInternal state) {
        boolean skip;
        try {
            skip = !task.getOnlyIf().isSatisfiedBy(task);
        } catch (Throwable t) {
            state.executed(new GradleException(String.format("Could not evaluate onlyIf predicate for %s.", task), t));
            return;
        }

        if (skip) {
            logger.info("Skipping execution as task onlyIf is false.");
            state.skipped("SKIPPED");
            return;
        }

        executer.execute(task, state);
    }
}
