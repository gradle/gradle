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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.OutputFilesSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;

import java.util.Map;

public class OutputFilesTaskStateChanges extends AbstractNamedFileSnapshotTaskStateChanges {
    private final OutputFilesSnapshotter outputSnapshotter;

    public OutputFilesTaskStateChanges(TaskExecution previous, TaskExecution current, TaskInternal task, FileCollectionSnapshotterRegistry snapshotterRegistry, OutputFilesSnapshotter outputSnapshotter) {
        super(task.getName(), previous, current, snapshotterRegistry, "Output", task.getOutputs().getFileProperties());
        this.outputSnapshotter = outputSnapshotter;
    }

    @Override
    public Map<String, FileCollectionSnapshot> getPrevious() {
        return previous.getOutputFilesSnapshot();
    }

    @Override
    public void saveCurrent() {
        final Map<String, FileCollectionSnapshot> outputFilesAfter = buildSnapshots(getTaskName(), getSnapshotterRegistry(), getTitle(), getFileProperties());

        ImmutableMap.Builder<String, FileCollectionSnapshot> builder = ImmutableMap.builder();
        for (TaskFilePropertySpec propertySpec : fileProperties) {
            String propertyName = propertySpec.getPropertyName();
            FileCollectionSnapshot beforeExecution = getCurrent().get(propertyName);
            FileCollectionSnapshot afterExecution = outputFilesAfter.get(propertyName);
            FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(propertyName);
            FileCollectionSnapshot outputSnapshot = outputSnapshotter.createOutputSnapshot(afterPreviousExecution, beforeExecution, afterExecution);
            builder.put(propertyName, outputSnapshot);
        }

        current.setOutputFilesSnapshot(builder.build());
    }

    private FileCollectionSnapshot getSnapshotAfterPreviousExecution(String propertyName) {
        if (previous != null) {
            Map<String, FileCollectionSnapshot> previousSnapshots = previous.getOutputFilesSnapshot();
            if (previousSnapshots != null) {
                FileCollectionSnapshot afterPreviousExecution = previousSnapshots.get(propertyName);
                if (afterPreviousExecution != null) {
                    return afterPreviousExecution;
                }
            }
        }
        return FileCollectionSnapshot.EMPTY;
    }
}
