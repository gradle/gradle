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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.Collection;

@NonNullApi
public class DefaultTaskUpToDateState implements TaskUpToDateState {

    private final TaskStateChanges inputFileChanges;
    private final OutputFileTaskStateChanges outputFileChanges;
    private final TaskStateChanges allTaskChanges;
    private final TaskStateChanges rebuildChanges;
    private final TaskStateChanges outputFilePropertyChanges;

    public DefaultTaskUpToDateState(TaskExecution lastExecution, TaskExecution thisExecution, TaskInternal task) {
        TaskStateChanges previousSuccessState = new PreviousSuccessTaskStateChanges(lastExecution);
        TaskStateChanges taskTypeState = new TaskTypeTaskStateChanges(lastExecution, thisExecution, task);
        TaskStateChanges inputPropertyChanges = new InputPropertyTaskStateChanges(lastExecution, thisExecution, task);
        TaskStateChanges inputPropertyValueChanges = new InputPropertyValueTaskStateChanges(lastExecution, thisExecution, task);

        // Capture outputs state
        this.outputFilePropertyChanges = new OutputPropertyTaskChanges(lastExecution, thisExecution, task);
        OutputFileTaskStateChanges uncachedOutputChanges = new OutputFileTaskStateChanges(lastExecution, thisExecution);
        TaskStateChanges outputFileChanges = caching(uncachedOutputChanges);
        this.outputFileChanges = uncachedOutputChanges;

        // Capture input files state
        TaskStateChanges inputFilePropertyChanges = new InputFilePropertyTaskStateChanges(lastExecution, thisExecution, task);
        InputFileTaskStateChanges directInputFileChanges = new InputFileTaskStateChanges(lastExecution, thisExecution);
        TaskStateChanges inputFileChanges = caching(directInputFileChanges);
        this.inputFileChanges = new ErrorHandlingTaskStateChanges(task, inputFileChanges);

        this.allTaskChanges = new ErrorHandlingTaskStateChanges(task, new SummaryTaskStateChanges(previousSuccessState, taskTypeState, inputPropertyChanges, inputPropertyValueChanges, outputFilePropertyChanges, outputFileChanges, inputFilePropertyChanges, inputFileChanges));
        this.rebuildChanges = new ErrorHandlingTaskStateChanges(task, new SummaryTaskStateChanges(previousSuccessState, taskTypeState, inputPropertyChanges, inputPropertyValueChanges, inputFilePropertyChanges, outputFilePropertyChanges, outputFileChanges));
    }

    private static TaskStateChanges caching(TaskStateChanges wrapped) {
        // TODO wolfs: implement caching for visitor TaskStateChange
//        return new CachingTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, wrapped);
        return wrapped;
    }

    @Override
    public Iterable<TaskStateChange> getInputFilesChanges() {
        return getChanges(inputFileChanges);
    }

    @Override
    public boolean hasAnyOutputFileChanges() {
        Collection<TaskStateChange> changes = getChanges(1, outputFilePropertyChanges);
        return !changes.isEmpty() || outputFileChanges.hasAnyChanges();
    }

    @Override
    public Iterable<TaskStateChange> getAllTaskChanges() {
        return getChanges(MAX_OUT_OF_DATE_MESSAGES, allTaskChanges);
    }

    @Override
    public Iterable<TaskStateChange> getRebuildChanges() {
        return getChanges(1, rebuildChanges);
    }

    private Collection<TaskStateChange> getChanges(TaskStateChanges stateChanges) {
        CollectingTaskStateChangeVisitor visitor = new CollectingTaskStateChangeVisitor();
        stateChanges.accept(visitor);
        return visitor.getChanges();
    }

    private Collection<TaskStateChange> getChanges(int maxReportedChanges, TaskStateChanges changes) {
        CollectingTaskStateChangeVisitor visitor = new CollectingTaskStateChangeVisitor();
        changes.accept(new MaximumNumberTaskStateChangeVisitor(maxReportedChanges, visitor));
        return visitor.getChanges();
    }
}
