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

import java.util.Optional;

public class DefaultExecutionStateChanges implements ExecutionStateChanges {

    private final AfterPreviousExecutionState previousExecution;
    private final ChangeContainer inputFileChanges;
    private final ChangeContainer allChanges;
    private final ChangeContainer rebuildTriggeringChanges;

    public DefaultExecutionStateChanges(AfterPreviousExecutionState lastExecution, BeforeExecutionState thisExecution, Describable executable, boolean includeAddedOutputs) {
        this.previousExecution = lastExecution;

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
        this.inputFileChanges = errorHandling(executable, inputFileChanges);

        // Capture output files state
        ChangeContainer outputFilePropertyChanges = new PropertyChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            "Output",
            executable);
        OutputFileChanges uncachedOutputChanges = new OutputFileChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            includeAddedOutputs);
        ChangeContainer outputFileChanges = caching(uncachedOutputChanges);

        this.allChanges = errorHandling(executable, new SummarizingChangeContainer(previousSuccessState, implementationChanges, inputPropertyChanges, inputPropertyValueChanges, outputFilePropertyChanges, outputFileChanges, inputFilePropertyChanges, inputFileChanges));
        this.rebuildTriggeringChanges = errorHandling(executable, new SummarizingChangeContainer(previousSuccessState, implementationChanges, inputPropertyChanges, inputPropertyValueChanges, inputFilePropertyChanges, outputFilePropertyChanges, outputFileChanges));
    }

    private static ChangeContainer caching(ChangeContainer wrapped) {
        return new CachingChangeContainer(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    private static ChangeContainer errorHandling(Describable executable, ChangeContainer wrapped) {
        return new ErrorHandlingChangeContainer(executable, wrapped);
    }

    @Override
    public Optional<Iterable<Change>> getInputFilesChanges() {
        if (isRebuildRequired()) {
            return Optional.empty();
        }
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        inputFileChanges.accept(visitor);
        return Optional.of(visitor.getChanges());
    }

    @Override
    public void visitAllChanges(ChangeVisitor visitor) {
        allChanges.accept(visitor);
    }

    private boolean isRebuildRequired() {
        ChangeDetectorVisitor changeDetectorVisitor = new ChangeDetectorVisitor();
        rebuildTriggeringChanges.accept(changeDetectorVisitor);
        return changeDetectorVisitor.hasAnyChanges();
    }

    @Override
    public AfterPreviousExecutionState getPreviousExecution() {
        return previousExecution;
    }
}
