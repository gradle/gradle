/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.Iterators;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import javax.annotation.Nullable;
import java.util.Iterator;

public class PreviousSuccessTaskStateChanges implements TaskStateChanges {
    private static final TaskStateChange PREVIOUS_FAILURE = new DescriptiveChange("Task has failed previously.");
    private final TaskExecution previousExecution;
    private final TaskExecution currentExecution;
    private final TaskInternal task;

    public PreviousSuccessTaskStateChanges(@Nullable TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        this.previousExecution = previousExecution;
        this.currentExecution = currentExecution;
        this.task = task;
    }

    @Override
    public Iterator<TaskStateChange> iterator() {
        if (previousExecution == null || previousExecution.isSuccessful()) {
            return Iterators.emptyIterator();
        } else {
            return Iterators.singletonIterator(PREVIOUS_FAILURE);
        }
    }
}
