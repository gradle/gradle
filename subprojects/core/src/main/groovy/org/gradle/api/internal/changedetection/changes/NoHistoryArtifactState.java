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

package org.gradle.api.internal.changedetection.changes;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.util.Collection;

class NoHistoryArtifactState implements TaskArtifactState, TaskExecutionHistory {
    public boolean isUpToDate(Collection<String> messages) {
        messages.add("Task has not declared any outputs.");
        return false;
    }

    public IncrementalTaskInputs getInputChanges() {
        throw new UnsupportedOperationException();
    }

    public TaskExecutionHistory getExecutionHistory() {
        return this;
    }

    public void beforeTask() {
    }

    public void afterTask() {
    }

    public void finished() {
    }

    public FileCollection getOutputFiles() {
        throw new UnsupportedOperationException();
    }
}
