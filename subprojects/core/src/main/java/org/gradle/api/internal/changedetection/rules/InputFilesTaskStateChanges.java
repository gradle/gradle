/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import javax.annotation.Nullable;
import java.util.Iterator;

public class InputFilesTaskStateChanges extends AbstractNamedFileSnapshotTaskStateChanges {
    public InputFilesTaskStateChanges(@Nullable TaskExecution previous, TaskExecution current, TaskInternal task) {
        super(previous, current, task, "Input");
    }

    @Override
    protected ImmutableSortedMap<String, FileCollectionSnapshot> getSnapshot(TaskExecution execution) {
        return execution.getInputFilesSnapshot();
    }

    @Override
    public Iterator<TaskStateChange> iterator() {
        return getFileChanges(true);
    }
}
