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

package org.gradle.api.internal.changedetection.changes;

import org.gradle.api.Action;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.util.ArrayList;
import java.util.List;

public class ChangesOnlyIncrementalTaskInputs extends StatefulIncrementalTaskInputs {
    private final Iterable<InputFileDetails> inputFilesState;
    private final List<InputFileDetails> removedFiles = new ArrayList<InputFileDetails>();

    public ChangesOnlyIncrementalTaskInputs(Iterable<InputFileDetails> inputFilesState) {
        this.inputFilesState = inputFilesState;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    protected void doOutOfDate(final Action<? super InputFileDetails> outOfDateAction) {
        for (InputFileDetails fileChange : inputFilesState) {
            if (fileChange.isRemoved()) {
                removedFiles.add(fileChange);
            } else {
                outOfDateAction.execute(fileChange);
            }
        }
    }

    @Override
    protected void doRemoved(Action<? super InputFileDetails> removedAction) {
        for (InputFileDetails removedFile : removedFiles) {
            removedAction.execute(removedFile);
        }
    }
}
