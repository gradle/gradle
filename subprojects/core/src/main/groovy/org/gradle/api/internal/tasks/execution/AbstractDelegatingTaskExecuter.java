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
package org.gradle.api.internal.tasks.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * A {@link TaskExecuter} which performs validation before executing the task.
 */
public abstract class AbstractDelegatingTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(AbstractDelegatingTaskExecuter.class);
    protected final TaskExecuter executer;

    public AbstractDelegatingTaskExecuter(TaskExecuter nextExecuter) {
        this.executer = nextExecuter;
    }

    /**
     * By default, it is assumed that this executer doesn't affect the up-to-datedness of the task
     */
    public boolean isCurrentlyUpToDate(TaskInternal task, TaskStateInternal state) {
        return executer.isCurrentlyUpToDate(task, state);
    }
}
