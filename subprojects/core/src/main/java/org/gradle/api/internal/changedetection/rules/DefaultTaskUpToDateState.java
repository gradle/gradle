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
        TaskStateChanges directInputFileChanges = new InputFileTaskStateChanges(lastExecution, thisExecution);
        TaskStateChanges inputFileChanges = caching(directInputFileChanges);
        this.inputFileChanges = new ErrorHandlingTaskStateChanges(task, inputFileChanges);

        // Capture discovered inputs state from previous execution
        DiscoveredInputTaskStateChanges discoveredChanges = new DiscoveredInputTaskStateChanges(lastExecution, thisExecution);
        TaskStateChanges discoveredInputFileChanges = caching(discoveredChanges);

        this.allTaskChanges = new ErrorHandlingTaskStateChanges(task, new SummaryTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, previousSuccessState, taskTypeState, inputPropertyChanges, inputPropertyValueChanges, outputFilePropertyChanges, outputFileChanges, inputFilePropertyChanges, inputFileChanges, discoveredInputFileChanges));
        this.rebuildChanges = new ErrorHandlingTaskStateChanges(task, new SummaryTaskStateChanges(1, previousSuccessState, taskTypeState, inputPropertyChanges, inputPropertyValueChanges, inputFilePropertyChanges, outputFilePropertyChanges, outputFileChanges));
    }

    private static TaskStateChanges caching(TaskStateChanges wrapped) {
        return new CachingTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    @Override
    public TaskStateChanges getInputFilesChanges() {
        return inputFileChanges;
    }

    @Override
    public boolean hasAnyOutputFileChanges() {
        return outputFilePropertyChanges.iterator().hasNext() || outputFileChanges.hasAnyChanges();
    }

    @Override
    public TaskStateChanges getAllTaskChanges() {
        return allTaskChanges;
    }

    @Override
    public TaskStateChanges getRebuildChanges() {
        return rebuildChanges;
    }
}
