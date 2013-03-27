/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.execution;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskInputChanges;
import org.gradle.api.internal.changedetection.ChangeType;

import java.io.File;

public class RebuildTaskInputChanges implements TaskInputChanges {
    private final Task task;
    private Action<InputFileChange> outOfDateAction;

    public RebuildTaskInputChanges(Task task) {
        this.task = task;
    }

    public boolean isAllOutOfDate() {
        return true;
    }

    public RebuildTaskInputChanges outOfDate(Action<InputFileChange> outOfDateAction) {
        this.outOfDateAction = outOfDateAction;
        return this;
    }

    public RebuildTaskInputChanges removed(Action<InputFileChange> removedAction) {
        return this;
    }

    public void process() {
        if (outOfDateAction == null) {
            throw new IllegalStateException("No outOfDate action specified");
        }
        for (File file : task.getInputs().getFiles()) {
            outOfDateAction.execute(new DefaultInputFileChange(file, ChangeType.UNSPECIFIED));
        }
    }
}
