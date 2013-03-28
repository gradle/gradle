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
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.execution.DefaultInputFileChange;

import java.util.ArrayList;
import java.util.List;

class IncrementalTaskInputChanges extends StatefulTaskInputChanges {
    private final TaskUpToDateState inputFilesState;
    private List<InputFileChange> removedFiles = new ArrayList<InputFileChange>();

    IncrementalTaskInputChanges(TaskUpToDateState inputFilesState) {
        this.inputFilesState = inputFilesState;
    }

    public boolean isAllOutOfDate() {
        return false;
    }

    @Override
    protected void doOutOfDate(final Action<? super InputFileChange> outOfDateAction) {
        inputFilesState.findChanges(new Action<TaskUpToDateStateChange>() {
            public void execute(TaskUpToDateStateChange change) {
                // TODO:DAZ Generify properly to avoid this check & cast
                assert change instanceof FileChange;
                FileChange fileChange = (FileChange) change;

                DefaultInputFileChange inputFileChange = new DefaultInputFileChange(fileChange.getFile(), fileChange.getChange());
                if (fileChange.getChange() == ChangeType.REMOVED) {
                    removedFiles.add(inputFileChange);
                } else {
                    outOfDateAction.execute(inputFileChange);
                }
            }
        });
    }

    @Override
    protected void doRemoved(Action<? super InputFileChange> removedAction) {
        for (InputFileChange removedFile : removedFiles) {
            removedAction.execute(removedFile);
        }
    }
}
