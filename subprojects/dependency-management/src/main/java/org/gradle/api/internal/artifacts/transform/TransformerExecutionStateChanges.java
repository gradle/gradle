/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Cast;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeDetectorVisitor;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.change.SummarizingChangeContainer;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.changes.AbstractFingerprintChanges;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputFileChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

public class TransformerExecutionStateChanges implements ExecutionStateChanges {
    private final InputFileChanges inputFileChanges;
    private final AllOutputFileChanges outputFileChanges;
    private final AfterPreviousExecutionState afterPreviousExecutionState;

    public TransformerExecutionStateChanges(InputFileChanges inputFileChanges, AllOutputFileChanges outputFileChanges, AfterPreviousExecutionState afterPreviousExecutionState) {
        this.inputFileChanges = inputFileChanges;
        this.outputFileChanges = outputFileChanges;
        this.afterPreviousExecutionState = afterPreviousExecutionState;
    }

    @Override
    public Iterable<Change> getInputFilesChanges() {
        return ImmutableList.of();
    }

    @Override
    public Iterable<InputFileDetails> getInputFilePropertyChanges(String propertyName) {
        CollectingChangeVisitor collectingChangeVisitor = new CollectingChangeVisitor();
        inputFileChanges.accept(propertyName, collectingChangeVisitor);
        return Cast.uncheckedNonnullCast(collectingChangeVisitor.getChanges());
    }

    @Override
    public void visitAllChanges(ChangeVisitor visitor) {
        new SummarizingChangeContainer(inputFileChanges, outputFileChanges).accept(visitor);
    }

    @Override
    public boolean isRebuildRequired() {
        ChangeDetectorVisitor changeDetectorVisitor = new ChangeDetectorVisitor();
        outputFileChanges.accept(changeDetectorVisitor);
        return changeDetectorVisitor.hasAnyChanges();
    }

    @Override
    public AfterPreviousExecutionState getPreviousExecution() {
        return afterPreviousExecutionState;
    }

    public static class AllOutputFileChanges extends AbstractFingerprintChanges {

        public AllOutputFileChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            super(previous, current, "Output");
        }

        @Override
        public boolean accept(ChangeVisitor visitor) {
            return accept(visitor, true);
        }
    }
}
