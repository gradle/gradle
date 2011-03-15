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
package org.gradle.api.internal.changedetection;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.TaskInternal;

import java.util.Collection;

/**
 * A rule which marks a task out-of-date when its implementation class changes.
 */
public class TaskTypeChangedUpToDateRule implements UpToDateRule {
    public TaskUpToDateState create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution) {
        final String taskClass = task.getClass().getName();
        currentExecution.setTaskClass(taskClass);

        return new TaskUpToDateState() {
            public void checkUpToDate(Collection<String> messages) {
                if (!taskClass.equals(previousExecution.getTaskClass())) {
                    messages.add(String.format("%s has changed type from '%s' to '%s'.", StringUtils.capitalize(task.toString()), previousExecution.getTaskClass(), task.getClass().getName()));
                }
            }

            public void snapshotAfterTask() {
            }
        };
    }
}
