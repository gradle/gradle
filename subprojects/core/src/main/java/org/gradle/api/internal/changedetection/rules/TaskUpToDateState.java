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

package org.gradle.api.internal.changedetection.rules;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;

/**
 * Represents the complete changes in a tasks state
 */
public class TaskUpToDateState {

    public static final int MAX_OUT_OF_DATE_MESSAGES = 3;

    private final TaskStateChanges inputFileChanges;
    private final OutputFileTaskStateChanges outputFileChanges;
    private final TaskStateChanges allTaskChanges;
    private final TaskStateChanges rebuildChanges;
    private final TaskStateChanges outputFilePropertyChanges;
    private final boolean hasLastExecution;

    public TaskUpToDateState(TaskExecution lastExecution, TaskExecution thisExecution, TaskInternal task) {
        hasLastExecution = lastExecution != null;
        if (!hasLastExecution) {
            NoHistoryTaskStateChanges noHistoryState = new NoHistoryTaskStateChanges();
            this.allTaskChanges = new SummaryTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, noHistoryState);
            this.rebuildChanges = new SummaryTaskStateChanges(1, noHistoryState);
            this.inputFileChanges = null;
            this.outputFileChanges = null;
            this.outputFilePropertyChanges = null;
        } else {
            TaskStateChanges previousSuccessState = new PreviousSuccessTaskStateChanges(lastExecution);
            TaskStateChanges taskTypeState = new TaskTypeTaskStateChanges(lastExecution, thisExecution, task);
            TaskStateChanges inputPropertyChanges = new InputPropertyTaskStateChanges(lastExecution, thisExecution, task);
            TaskStateChanges inputPropertyValueChanges = new InputPropertyValueTaskStateChanges(lastExecution, thisExecution, task);

            // Capture outputs state
            this.outputFilePropertyChanges = new OutputFilePropertyTaskChanges(lastExecution, thisExecution, task);
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
    }

    private static TaskStateChanges caching(TaskStateChanges wrapped) {
        return new CachingTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    /**
     * Returns changes to input files only.
     */
    public TaskStateChanges getInputFilesChanges() {
        if (inputFileChanges == null) {
            throw new IllegalStateException("Input file changes can only be queried when the task has been executed before");
        }
        return inputFileChanges;
    }

    /**
     * Returns if any output files have been changed, added or removed.
     */
    public boolean hasAnyOutputFileChanges() {
        return !hasLastExecution || outputFilePropertyChanges.iterator().hasNext() || outputFileChanges.hasAnyChanges();
    }

    /**
     * Returns any change to task inputs or outputs.
     */
    public TaskStateChanges getAllTaskChanges() {
        return allTaskChanges;
    }

    /**
     * Returns changes that would force an incremental task to fully rebuild.
     */
    public TaskStateChanges getRebuildChanges() {
        return rebuildChanges;
    }
}
