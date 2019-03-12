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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Describable;
import org.gradle.internal.change.CachingChangeContainer;
import org.gradle.internal.change.ChangeContainer;
import org.gradle.internal.change.ChangeDetectorVisitor;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.ErrorHandlingChangeContainer;
import org.gradle.internal.change.SummarizingChangeContainer;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.impl.IncrementalInputChanges;
import org.gradle.internal.execution.history.impl.NonIncrementalInputChanges;

public class DefaultExecutionStateChangeDetector implements ExecutionStateChangeDetector {
    @Override
    public ExecutionStateChanges detectChanges(AfterPreviousExecutionState lastExecution, BeforeExecutionState thisExecution, UnitOfWork work, boolean allowOverlappingOutputs) {
        // Capture changes in execution outcome
        ChangeContainer previousSuccessState = new PreviousSuccessChanges(
            lastExecution.isSuccessful());

        // Capture changes to implementation
        ChangeContainer implementationChanges = new ImplementationChanges(
            lastExecution.getImplementation(), lastExecution.getAdditionalImplementations(),
            thisExecution.getImplementation(), thisExecution.getAdditionalImplementations(),
            work);

        // Capture non-file input changes
        ChangeContainer inputPropertyChanges = new PropertyChanges(
            lastExecution.getInputProperties(),
            thisExecution.getInputProperties(),
            "Input",
            work);
        ChangeContainer inputPropertyValueChanges = new InputValueChanges(
            lastExecution.getInputProperties(),
            thisExecution.getInputProperties(),
            work);

        // Capture input files state
        ChangeContainer inputFilePropertyChanges = new PropertyChanges(
            lastExecution.getInputFileProperties(),
            thisExecution.getInputFileProperties(),
            "Input file",
            work);
        InputFileChanges directInputFileChanges = new DefaultInputFileChanges(
            lastExecution.getInputFileProperties(),
            thisExecution.getInputFileProperties());
        InputFileChanges inputFileChanges = caching(directInputFileChanges);

        // Capture output files state
        ChangeContainer outputFilePropertyChanges = new PropertyChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            "Output",
            work);
        OutputFileChanges uncachedOutputChanges = new OutputFileChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            allowOverlappingOutputs);
        ChangeContainer outputFileChanges = caching(uncachedOutputChanges);

        return new DetectedExecutionStateChanges(
            errorHandling(work, inputFileChanges),
            errorHandling(work, new SummarizingChangeContainer(previousSuccessState, implementationChanges, inputPropertyChanges, inputPropertyValueChanges, outputFilePropertyChanges, outputFileChanges, inputFilePropertyChanges, inputFileChanges)),
            errorHandling(work, new SummarizingChangeContainer(previousSuccessState, implementationChanges, inputPropertyChanges, inputPropertyValueChanges, inputFilePropertyChanges, outputFilePropertyChanges, outputFileChanges)),
            thisExecution,
            work
        );
    }

    private static ChangeContainer caching(ChangeContainer wrapped) {
        return new CachingChangeContainer(MAX_OUT_OF_DATE_MESSAGES, wrapped);
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

    private static class DetectedExecutionStateChanges implements ExecutionStateChanges {
        private final InputFileChanges inputFileChanges;
        private final ChangeContainer allChanges;
        private final ChangeContainer rebuildTriggeringChanges;
        private final BeforeExecutionState thisExecution;
        private final UnitOfWork work;

        public DetectedExecutionStateChanges(
            InputFileChanges inputFileChanges,
            ChangeContainer allChanges,
            ChangeContainer rebuildTriggeringChanges,
            BeforeExecutionState thisExecution,
            UnitOfWork work
        ) {
            this.inputFileChanges = inputFileChanges;
            this.allChanges = allChanges;
            this.rebuildTriggeringChanges = rebuildTriggeringChanges;
            this.thisExecution = thisExecution;
            this.work = work;
        }

        @Override
        public void visitAllChanges(ChangeVisitor visitor) {
            allChanges.accept(visitor);
        }

        @Override
        public InputChangesInternal getInputChanges() {
            ImmutableMap<Object, String> inputToPropertyNames = work.getInputToPropertyNames();
            return isRebuildRequired()
                ? new NonIncrementalInputChanges(thisExecution.getInputFileProperties(), inputToPropertyNames, work)
                : new IncrementalInputChanges(inputFileChanges, inputToPropertyNames);
        }

        private boolean isRebuildRequired() {
            ChangeDetectorVisitor changeDetectorVisitor = new ChangeDetectorVisitor();
            rebuildTriggeringChanges.accept(changeDetectorVisitor);
            return changeDetectorVisitor.hasAnyChanges();
        }
    }
}
