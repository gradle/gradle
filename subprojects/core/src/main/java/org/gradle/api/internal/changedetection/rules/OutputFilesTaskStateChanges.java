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
import com.google.common.collect.Maps;
import org.gradle.api.Nullable;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.snapshotting.SnapshottingConfigurationInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.OutputFilesSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.Map;

public class OutputFilesTaskStateChanges extends AbstractNamedFileSnapshotTaskStateChanges {
    private final OutputFilesSnapshotter outputSnapshotter;

    public OutputFilesTaskStateChanges(@Nullable TaskExecution previous, TaskExecution current, TaskInternal task, FileCollectionSnapshotterRegistry snapshotterRegistry, OutputFilesSnapshotter outputSnapshotter) {
        super(task.getName(), previous, current, snapshotterRegistry, "Output", task.getOutputs().getFileProperties(), (SnapshottingConfigurationInternal) task.getProject().getSnapshotting());
        this.outputSnapshotter = outputSnapshotter;
        detectOverlappingOutputs();
    }

    @Override
    public ImmutableSortedMap<String, FileCollectionSnapshot> getPrevious() {
        return previous.getOutputFilesSnapshot();
    }

    @Override
    public void saveCurrent() {
        final ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesAfter = buildSnapshots(getTaskName(), getSnapshotterRegistry(), getTitle(), getFileProperties(), getSnapshottingConfiguration());

        ImmutableSortedMap<String, FileCollectionSnapshot> results = ImmutableSortedMap.copyOfSorted(Maps.transformEntries(getCurrent(), new Maps.EntryTransformer<String, FileCollectionSnapshot, FileCollectionSnapshot>() {
            @Override
            public FileCollectionSnapshot transformEntry(String propertyName, FileCollectionSnapshot beforeExecution) {
                FileCollectionSnapshot afterExecution = outputFilesAfter.get(propertyName);
                FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(propertyName);
                return outputSnapshotter.createOutputSnapshot(afterPreviousExecution, beforeExecution, afterExecution);
            }
        }));
        current.setOutputFilesSnapshot(results);
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

    private void detectOverlappingOutputs() {
        for (Map.Entry<String, FileCollectionSnapshot> entry : getCurrent().entrySet()) {
            String propertyName = entry.getKey();
            FileCollectionSnapshot beforeExecution = entry.getValue();
            FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(propertyName);
            TaskExecutionHistory.OverlappingOutputs overlappingOutputs = outputSnapshotter.detectOverlappingOutputs(propertyName, afterPreviousExecution, beforeExecution);
            if (overlappingOutputs !=null) {
                current.setDetectedOverlappingOutputs(overlappingOutputs);
                return;
            }
        }
    }
}
