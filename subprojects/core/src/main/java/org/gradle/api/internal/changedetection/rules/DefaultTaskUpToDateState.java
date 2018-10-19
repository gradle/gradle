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

package org.gradle.api.internal.changedetection.rules;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.internal.change.CachingChangeContainer;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeContainer;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.change.ErrorHandlingChangeContainer;
import org.gradle.internal.change.SummarizingChangeContainer;

public class DefaultTaskUpToDateState implements TaskUpToDateState {

    private final ChangeContainer inputFileChanges;
    private final OutputFileChanges outputFileChanges;
    private final ChangeContainer allChanges;
    private final ChangeContainer rebuildChanges;
    private final ChangeContainer outputFilePropertyChanges;

    public DefaultTaskUpToDateState(TaskExecution lastExecution, TaskExecution thisExecution, TaskInternal task) {
        ChangeContainer previousSuccessState = new PreviousSuccessChanges(lastExecution);
        ChangeContainer taskTypeState = new ImplementationStateChanges(lastExecution, thisExecution, task);
        ChangeContainer inputPropertyChanges = new InputPropertyChanges(lastExecution, thisExecution, task);
        ChangeContainer inputPropertyValueChanges = new InputPropertyValueChanges(lastExecution, thisExecution, task);

        // Capture outputs state
        this.outputFilePropertyChanges = new OutputPropertyChanges(lastExecution, thisExecution, task);
        OutputFileChanges uncachedOutputChanges = new OutputFileChanges(lastExecution, thisExecution);
        ChangeContainer outputFileChanges = caching(uncachedOutputChanges);
        this.outputFileChanges = uncachedOutputChanges;

        // Capture input files state
        ChangeContainer inputFilePropertyChanges = new InputFilePropertyChanges(lastExecution, thisExecution, task);
        InputFileChanges directInputFileChanges = new InputFileChanges(lastExecution, thisExecution);
        ChangeContainer inputFileChanges = caching(directInputFileChanges);
        this.inputFileChanges = new ErrorHandlingChangeContainer(task, inputFileChanges);

        this.allChanges = new ErrorHandlingChangeContainer(task, new SummarizingChangeContainer(previousSuccessState, taskTypeState, inputPropertyChanges, inputPropertyValueChanges, outputFilePropertyChanges, outputFileChanges, inputFilePropertyChanges, inputFileChanges));
        this.rebuildChanges = new ErrorHandlingChangeContainer(task, new SummarizingChangeContainer(previousSuccessState, taskTypeState, inputPropertyChanges, inputPropertyValueChanges, inputFilePropertyChanges, outputFilePropertyChanges, outputFileChanges));
    }

    private static ChangeContainer caching(ChangeContainer wrapped) {
        return new CachingChangeContainer(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    @Override
    public Iterable<Change> getInputFilesChanges() {
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        inputFileChanges.accept(visitor);
        return visitor.getChanges();
    }

    @Override
    public boolean hasAnyOutputFileChanges() {
        ChangeDetectorVisitor visitor = new ChangeDetectorVisitor();
        outputFilePropertyChanges.accept(visitor);
        return visitor.hasAnyChanges() || outputFileChanges.hasAnyChanges();
    }

    @Override
    public void visitAllChanges(ChangeVisitor visitor) {
        allChanges.accept(visitor);
    }

    @Override
    public boolean isRebuildRequired() {
        ChangeDetectorVisitor changeDetectorVisitor = new ChangeDetectorVisitor();
        rebuildChanges.accept(changeDetectorVisitor);
        return changeDetectorVisitor.hasAnyChanges();
    }

}
