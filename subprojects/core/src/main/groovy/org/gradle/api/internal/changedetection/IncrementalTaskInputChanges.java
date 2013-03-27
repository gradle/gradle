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

package org.gradle.api.internal.changedetection;

import org.gradle.api.Action;
import org.gradle.api.execution.TaskInputChanges;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.execution.DefaultInputFileChange;

class IncrementalTaskInputChanges implements TaskInputChanges {
    private final TaskUpToDateState inputFilesState;
    private Action<InputFileChange> outOfDateAction;
    private Action<InputFileChange> removedAction;

    IncrementalTaskInputChanges(TaskUpToDateState inputFilesState) {
        this.inputFilesState = inputFilesState;
    }

    public boolean isAllOutOfDate() {
        return false;
    }

    public TaskInputChanges outOfDate(Action<InputFileChange> outOfDateAction) {
        this.outOfDateAction = outOfDateAction;
        return this;
    }

    public TaskInputChanges removed(Action<InputFileChange> removedAction) {
        this.removedAction = removedAction;
        return this;
    }

    public void process() {
        inputFilesState.findChanges(new Action<TaskUpToDateStateChange>() {
            public void execute(TaskUpToDateStateChange change) {
                // TODO:DAZ Generify properly to avoid this check & cast
                assert change instanceof FileChange;
                FileChange fileChange = (FileChange) change;

                DefaultInputFileChange inputFileChange = new DefaultInputFileChange(fileChange.getFile(), fileChange.getChange());
                if (fileChange.getChange() == ChangeType.REMOVED) {
                    if (removedAction != null) {
                        removedAction.execute(inputFileChange);
                    }
                } else {
                    if (outOfDateAction != null) {
                        outOfDateAction.execute(inputFileChange);
                    }
                }
            }
        });
    }
}
