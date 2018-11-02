/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.DescriptiveChange;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;

public class NoHistoryTaskUpToDateState implements ExecutionStateChanges {

    public static final NoHistoryTaskUpToDateState INSTANCE = new NoHistoryTaskUpToDateState();

    private final DescriptiveChange noHistoryChange = new DescriptiveChange("No history is available.");

    @Override
    public Iterable<Change> getInputFilesChanges() {
        throw new UnsupportedOperationException("Input file changes can only be queried when task history is available.");
    }

    @Override
    public void visitAllChanges(ChangeVisitor visitor) {
        visitor.visitChange(noHistoryChange);
    }

    @Override
    public boolean isRebuildRequired() {
        return true;
    }

    @Override
    public AfterPreviousExecutionState getPreviousExecution() {
        throw new UnsupportedOperationException();
    }
}
