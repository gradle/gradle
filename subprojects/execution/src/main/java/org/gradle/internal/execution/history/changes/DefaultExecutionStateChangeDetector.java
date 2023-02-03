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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Describable;
import org.gradle.internal.Cast;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.impl.ClassImplementationSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import static org.gradle.internal.execution.history.impl.OutputSnapshotUtil.findOutputsStillPresentSincePreviousExecution;

public class DefaultExecutionStateChangeDetector implements ExecutionStateChangeDetector {
    @Override
    public ExecutionStateChanges detectChanges(
        Describable executable,
        PreviousExecutionState lastExecution,
        BeforeExecutionState thisExecution,
        IncrementalInputProperties incrementalInputProperties
    ) {
        // Capture changes in execution outcome
        ChangeContainer previousSuccessState = new PreviousSuccessChanges(
            lastExecution.isSuccessful());

        // Capture changes to implementation

        // After validation, the current implementations and previous implementations can't be unknown.
        // Moreover, they need to come from an actual Class, not a lambda, since there isn't a way for a unit of work implementation to be a lambda.
        ClassImplementationSnapshot currentImplementation = Cast.uncheckedNonnullCast(thisExecution.getImplementation());
        ClassImplementationSnapshot previousImplementation = Cast.uncheckedNonnullCast(lastExecution.getImplementation());
        ImmutableList<ImplementationSnapshot> currentAdditionalImplementations = Cast.uncheckedNonnullCast(thisExecution.getAdditionalImplementations());
        ChangeContainer implementationChanges = new ImplementationChanges(
            previousImplementation, lastExecution.getAdditionalImplementations(),
            currentImplementation, currentAdditionalImplementations,
            executable);

        // Capture non-file input changes
        ChangeContainer inputPropertyChanges = new PropertyChanges(
            lastExecution.getInputProperties().keySet(),
            thisExecution.getInputProperties().keySet(),
            "Input",
            executable);
        ChangeContainer inputPropertyValueChanges = new InputValueChanges(
            lastExecution.getInputProperties(),
            thisExecution.getInputProperties(),
            executable);

        // Capture input files state
        ChangeContainer inputFilePropertyChanges = new PropertyChanges(
            lastExecution.getInputFileProperties().keySet(),
            thisExecution.getInputFileProperties().keySet(),
            "Input file",
            executable);
        InputFileChanges nonIncrementalInputFileChanges = incrementalInputProperties.nonIncrementalChanges(
            lastExecution.getInputFileProperties(),
            thisExecution.getInputFileProperties()
        );

        // Capture output files state
        ChangeContainer outputFilePropertyChanges = new PropertyChanges(
            lastExecution.getOutputFilesProducedByWork().keySet(),
            thisExecution.getOutputFileLocationSnapshots().keySet(),
            "Output",
            executable);
        ImmutableSortedMap<String, FileSystemSnapshot> remainingPreviouslyProducedOutputs = thisExecution.getDetectedOverlappingOutputs().isPresent()
            ? findOutputsStillPresentSincePreviousExecution(lastExecution.getOutputFilesProducedByWork(), thisExecution.getOutputFileLocationSnapshots())
            : thisExecution.getOutputFileLocationSnapshots();
        OutputFileChanges outputFileChanges = new OutputFileChanges(
            lastExecution.getOutputFilesProducedByWork(),
            remainingPreviouslyProducedOutputs
        );

        // Collect changes that would trigger a rebuild
        ChangeContainer rebuildTriggeringChanges = errorHandling(executable, new SummarizingChangeContainer(
            previousSuccessState,
            implementationChanges,
            inputPropertyChanges,
            inputPropertyValueChanges,
            outputFilePropertyChanges,
            outputFileChanges,
            inputFilePropertyChanges,
            nonIncrementalInputFileChanges
        ));
        ImmutableList<String> rebuildReasons = collectChanges(rebuildTriggeringChanges);

        if (!rebuildReasons.isEmpty()) {
            return ExecutionStateChanges.nonIncremental(
                rebuildReasons,
                thisExecution,
                incrementalInputProperties
            );
        } else {
            // Collect incremental input changes
            InputFileChanges directIncrementalInputFileChanges = incrementalInputProperties.incrementalChanges(
                lastExecution.getInputFileProperties(),
                thisExecution.getInputFileProperties()
            );
            InputFileChanges incrementalInputFileChanges = errorHandling(executable, caching(directIncrementalInputFileChanges));
            ImmutableList<String> incrementalInputFileChangeMessages = collectChanges(incrementalInputFileChanges);
            return ExecutionStateChanges.incremental(
                incrementalInputFileChangeMessages,
                thisExecution,
                incrementalInputFileChanges,
                incrementalInputProperties
            );
        }
    }

    private static ImmutableList<String> collectChanges(ChangeContainer changes) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        MessageCollectingChangeVisitor visitor = new MessageCollectingChangeVisitor(builder, ExecutionStateChangeDetector.MAX_OUT_OF_DATE_MESSAGES);
        changes.accept(visitor);
        return builder.build();
    }

    private static InputFileChanges caching(InputFileChanges wrapped) {
        CachingChangeContainer cachingChangeContainer = new CachingChangeContainer(MAX_OUT_OF_DATE_MESSAGES, wrapped);
        return new InputFileChangesWrapper(wrapped, cachingChangeContainer);
    }

    private static ChangeContainer errorHandling(Describable executable, ChangeContainer wrapped) {
        return new ErrorHandlingChangeContainer(executable, wrapped);
    }

    private static InputFileChanges errorHandling(Describable executable, InputFileChanges wrapped) {
        ErrorHandlingChangeContainer errorHandlingChangeContainer = new ErrorHandlingChangeContainer(executable, wrapped);
        return new InputFileChangesWrapper(wrapped, errorHandlingChangeContainer);
    }

    private static class InputFileChangesWrapper implements InputFileChanges {
        private final InputFileChanges inputFileChangesDelegate;
        private final ChangeContainer changeContainerDelegate;

        public InputFileChangesWrapper(InputFileChanges inputFileChangesDelegate, ChangeContainer changeContainerDelegate) {
            this.inputFileChangesDelegate = inputFileChangesDelegate;
            this.changeContainerDelegate = changeContainerDelegate;
        }

        @Override
        public boolean accept(String propertyName, ChangeVisitor visitor) {
            return inputFileChangesDelegate.accept(propertyName, visitor);
        }

        @Override
        public boolean accept(ChangeVisitor visitor) {
            return changeContainerDelegate.accept(visitor);
        }
    }

    private static class MessageCollectingChangeVisitor implements ChangeVisitor {
        private final ImmutableCollection.Builder<String> messages;
        private final int max;
        private int count;

        public MessageCollectingChangeVisitor(ImmutableCollection.Builder<String> messages, int max) {
            this.messages = messages;
            this.max = max;
        }

        @Override
        public boolean visitChange(Change change) {
            messages.add(change.getMessage());
            return ++count < max;
        }
    }
}
