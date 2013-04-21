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
import org.gradle.util.DeprecationLogger;

import java.beans.PropertyChangeEvent;

public class TaskStatusNagger {
    private static final String DEPRECATION_MESSAGE = "Calling %s after task execution has started";
    private static final String EXPLANAITION = "Check the configuration of %s";
    private static final String EXPLANAITION_WITH_HINT = EXPLANAITION + ". You may have misused '<<' at task declaration";

    private final TaskInternal taskInternal;
    private boolean nagUser = true;
    private boolean executingleftShiftAction;

    public TaskStatusNagger(TaskInternal taskInternal) {
        this.taskInternal = taskInternal;
    }

    public void nagIfTaskNotInConfigurableState(String method) {
        if (!taskInternal.getStateInternal().isConfigurable() && nagUser) {
            warn(method);
        }
    }

    public void nagAboutMutatingListIfTaskNotInConfigurableState(String listname, PropertyChangeEvent evt) {
        if (!taskInternal.getStateInternal().isConfigurable() && nagUser) {
            if (evt instanceof ObservableList.ElementEvent) {
                switch (((ObservableList.ElementEvent) evt).getChangeType()) {
                    case ADDED:
                        warn(String.format("%s.%s", listname, "add()"));
                        break;
                    case UPDATED:
                        warn(String.format("%s.%s", listname, "set(int, Object)"));
                        break;
                    case REMOVED:
                        warn(String.format("%s.%s", listname, "remove()"));
                        break;
                    case CLEARED:
                        warn(String.format("%s.%s", listname, "clear()"));
                        break;
                    case MULTI_ADD:
                        warn(String.format("%s.%s", listname, "addAll()"));
                        break;
                    case MULTI_REMOVE:
                        warn(String.format("%s.%s", listname, "removeAll()"));
                        break;
                }
            }
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

    private void warn(String method) {
        if (executingleftShiftAction) {
            DeprecationLogger.nagUserOfDeprecated(String.format(DEPRECATION_MESSAGE, method), String.format(EXPLANAITION_WITH_HINT, taskInternal));
        } else {
            DeprecationLogger.nagUserOfDeprecated(String.format(DEPRECATION_MESSAGE, method), String.format(EXPLANAITION, taskInternal));
        }
    }

    public void whileDisabled(Runnable runnable) {
        nagUser = false;
        try {
            runnable.run();
        } finally {
            nagUser = true;
        }
    }
}
