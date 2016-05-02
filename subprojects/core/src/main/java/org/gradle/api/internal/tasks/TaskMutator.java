/*
 * Copyright 2012 the original author or authors.
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

import groovy.util.ObservableList;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;

import java.beans.PropertyChangeEvent;

public class TaskMutator {
    private final TaskInternal task;
    private boolean executingleftShiftAction;

    public TaskMutator(TaskInternal task) {
        this.task = task;
    }

    public void mutate(String method, Runnable action) {
        if (!task.getState().isConfigurable()) {
            throw new IllegalStateException(format(method));
        }
        action.run();
    }

    public void assertMutable(String listname, PropertyChangeEvent evt) {
        String method = null;
        if (evt instanceof ObservableList.ElementEvent) {
            switch (((ObservableList.ElementEvent) evt).getChangeType()) {
                case ADDED:
                    method = String.format("%s.%s", listname, "add()");
                    break;
                case UPDATED:
                    method = String.format("%s.%s", listname, "set(int, Object)");
                    break;
                case REMOVED:
                    method = String.format("%s.%s", listname, "remove()");
                    break;
                case CLEARED:
                    method = String.format("%s.%s", listname, "clear()");
                    break;
                case MULTI_ADD:
                    method = String.format("%s.%s", listname, "addAll()");
                    break;
                case MULTI_REMOVE:
                    method = String.format("%s.%s", listname, "removeAll()");
                    break;
            }
        }
        if (method == null) {
            return;
        }
        if (!task.getState().isConfigurable()) {
            throw new IllegalStateException(format(method));
        }
    }

    public ContextAwareTaskAction leftShift(final ContextAwareTaskAction action) {
        return new ContextAwareTaskAction() {
            public void execute(Task task) {
                executingleftShiftAction = true;
                try {
                    action.execute(task);
                } finally {
                    executingleftShiftAction = false;
                }
            }

            public void contextualise(TaskExecutionContext context) {
                action.contextualise(context);
            }
        };
    }

    private String format(String method) {
        if (executingleftShiftAction) {
            return String.format("Cannot call %s on %s after task has started execution. Check the configuration of %s as you may have misused '<<' at task declaration.", method, task, task);
        }
        return String.format("Cannot call %s on %s after task has started execution.", method, task);
    }
}
