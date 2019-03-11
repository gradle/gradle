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

package org.gradle.internal.execution.history.changes;

import org.gradle.api.Describable;
import org.gradle.internal.change.CachingChangeContainer;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeContainer;
import org.gradle.internal.change.ChangeDetectorVisitor;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.change.ErrorHandlingChangeContainer;
import org.gradle.internal.change.SummarizingChangeContainer;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;

public class DefaultExecutionStateChangeDetector implements ExecutionStateChangeDetector {
    @Override
    public ExecutionStateChanges detectChanges(AfterPreviousExecutionState lastExecution, BeforeExecutionState thisExecution, Describable executable, boolean allowOverlappingOutputs) {
        // Capture changes in execution outcome
        ChangeContainer previousSuccessState = new PreviousSuccessChanges(
            lastExecution.isSuccessful());

        // Capture changes to implementation
        ChangeContainer implementationChanges = new ImplementationChanges(
            lastExecution.getImplementation(), lastExecution.getAdditionalImplementations(),
            thisExecution.getImplementation(), thisExecution.getAdditionalImplementations(),
            executable);

        // Capture non-file input changes
        ChangeContainer inputPropertyChanges = new PropertyChanges(
            lastExecution.getInputProperties(),
            thisExecution.getInputProperties(),
            "Input",
            executable);
        ChangeContainer inputPropertyValueChanges = new InputValueChanges(
            lastExecution.getInputProperties(),
            thisExecution.getInputProperties(),
            executable);

        // Capture input files state
        ChangeContainer inputFilePropertyChanges = new PropertyChanges(
            lastExecution.getInputFileProperties(),
            thisExecution.getInputFileProperties(),
            "Input file",
            executable);
        InputFileChanges directInputFileChanges = new InputFileChanges(
            lastExecution.getInputFileProperties(),
            thisExecution.getInputFileProperties());
        ChangeContainer inputFileChanges = caching(directInputFileChanges);

        // Capture output files state
        ChangeContainer outputFilePropertyChanges = new PropertyChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            "Output",
            executable);
        OutputFileChanges uncachedOutputChanges = new OutputFileChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            allowOverlappingOutputs);
        ChangeContainer outputFileChanges = caching(uncachedOutputChanges);

        return new DetectedExecutionStateChanges(
            errorHandling(executable, inputFileChanges),
            errorHandling(executable, new SummarizingChangeContainer(previousSuccessState, implementationChanges, inputPropertyChanges, inputPropertyValueChanges, outputFilePropertyChanges, outputFileChanges, inputFilePropertyChanges, inputFileChanges)),
            errorHandling(executable, new SummarizingChangeContainer(previousSuccessState, implementationChanges, inputPropertyChanges, inputPropertyValueChanges, inputFilePropertyChanges, outputFilePropertyChanges, outputFileChanges)),
            thisExecution
        );
    }

    private static ChangeContainer caching(ChangeContainer wrapped) {
        return new CachingChangeContainer(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    private static ChangeContainer errorHandling(Describable executable, ChangeContainer wrapped) {
        return new ErrorHandlingChangeContainer(executable, wrapped);
    }

    private static class DetectedExecutionStateChanges implements ExecutionStateChanges {
        private final ChangeContainer inputFileChanges;
        private final ChangeContainer allChanges;
        private final ChangeContainer rebuildTriggeringChanges;
        private final BeforeExecutionState thisExecution;

        public DetectedExecutionStateChanges(
            ChangeContainer inputFileChanges,
            ChangeContainer allChanges,
            ChangeContainer rebuildTriggeringChanges,
            BeforeExecutionState thisExecution
        ) {
            this.inputFileChanges = inputFileChanges;
            this.allChanges = allChanges;
            this.rebuildTriggeringChanges = rebuildTriggeringChanges;
            this.thisExecution = thisExecution;
        }

        @Override
        public void visitAllChanges(ChangeVisitor visitor) {
            allChanges.accept(visitor);
        }

        @Override
        public <T> T visitInputFileChanges(IncrementalInputsVisitor<T> visitor) {
            return isRebuildRequired() ?
                visitor.visitRebuild(thisExecution.getInputFileProperties()) : visitor.visitIncrementalChange(collectInputFileChanges());
        }

        private boolean isRebuildRequired() {
            ChangeDetectorVisitor changeDetectorVisitor = new ChangeDetectorVisitor();
            rebuildTriggeringChanges.accept(changeDetectorVisitor);
            return changeDetectorVisitor.hasAnyChanges();
        }

        private Iterable<Change> collectInputFileChanges() {
            CollectingChangeVisitor visitor = new CollectingChangeVisitor();
            inputFileChanges.accept(visitor);
            return visitor.getChanges();
        }
    }
}
