/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes a snapshot of the output files of a task.
 */
public class OutputFilesCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileCollectionSnapshotter snapshotter;
    private final StringInterner stringInterner;

    public OutputFilesCollectionSnapshotter(FileCollectionSnapshotter snapshotter, StringInterner stringInterner) {
        this.snapshotter = snapshotter;
        this.stringInterner = stringInterner;
    }

    public void registerSerializers(SerializerRegistry registry) {
        DefaultSerializerRegistry nested = new DefaultSerializerRegistry();
        snapshotter.registerSerializers(nested);
        registry.register(OutputFilesCollectionSnapshot.class, new OutputFilesCollectionSnapshot.SerializerImpl(nested.build(FileCollectionSnapshot.class), stringInterner));
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new OutputFilesCollectionSnapshot(Collections.<String, Boolean>emptyMap(), snapshotter.emptySnapshot());
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection files, TaskFilePropertyCompareStrategy compareStrategy, TaskFilePropertyPathSensitivity pathSensitivity) {
        return new OutputFilesCollectionSnapshot(getRoots(files), snapshotter.snapshot(files, compareStrategy, pathSensitivity));
    }

    @Override
    public FileCollectionSnapshot snapshot(TaskFilePropertySpec propertySpec) {
        return new OutputFilesCollectionSnapshot(getRoots(propertySpec.getPropertyFiles()), snapshotter.snapshot(propertySpec));
    }

    private Map<String, Boolean> getRoots(FileCollection files) {
        Map<String, Boolean> roots = new HashMap<String, Boolean>();
        for (File file : files.getFiles()) {
            roots.put(stringInterner.intern(file.getAbsolutePath()), file.exists());
        }
        return roots;
    }

    /**
     * Returns a new snapshot that filters out entries that should not be considered outputs of the task.
     */
    public OutputFilesCollectionSnapshot createOutputSnapshot(
        FileCollectionSnapshot afterPreviousExecution,
        FileCollectionSnapshot beforeExecution,
        FileCollectionSnapshot afterExecution,
        FileCollection roots
    ) {
        FileCollectionSnapshot filesSnapshot;
        Map<String, NormalizedFileSnapshot> afterSnapshots = afterExecution.getSnapshots();
        if (!beforeExecution.getSnapshots().isEmpty() && !afterSnapshots.isEmpty()) {
            Map<String, NormalizedFileSnapshot> beforeSnapshots = beforeExecution.getSnapshots();
            Map<String, NormalizedFileSnapshot> afterPreviousSnapshots = afterPreviousExecution != null ? afterPreviousExecution.getSnapshots() : new HashMap<String, NormalizedFileSnapshot>();
            int newEntryCount = 0;
            ImmutableMap.Builder<String, NormalizedFileSnapshot> outputEntries = ImmutableMap.builder();

            for (Map.Entry<String, NormalizedFileSnapshot> entry : afterSnapshots.entrySet()) {
                final String path = entry.getKey();
                NormalizedFileSnapshot fileSnapshot = entry.getValue();
                if (isOutputEntry(path, fileSnapshot, beforeSnapshots, afterPreviousSnapshots)) {
                    outputEntries.put(entry.getKey(), fileSnapshot);
                    newEntryCount++;
                }
            }
            // Are all files snapshot after execution accounted for as new entries?
            if (newEntryCount == afterSnapshots.size()) {
                filesSnapshot = unwrap(afterExecution);
            } else {
                filesSnapshot = new DefaultFileCollectionSnapshot(outputEntries.build(), TaskFilePropertyCompareStrategy.OUTPUT);
            }
        } else {
            filesSnapshot = unwrap(afterExecution);
        }
        return new OutputFilesCollectionSnapshot(getRoots(roots), filesSnapshot);
    }

    /**
     * Decide whether an entry should be considered to be part of the output. Entries that are considered outputs are:
     * <ul>
     *     <li>an entry that did not exist before the execution, but exists after the execution</li>
     *     <li>an entry that did exist before the execution, and has been changed durign the execution</li>
     *     <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
     * </ul>
     */
    private static boolean isOutputEntry(String path, NormalizedFileSnapshot fileSnapshot, Map<String, NormalizedFileSnapshot> beforeSnapshots, Map<String, NormalizedFileSnapshot> afterPreviousSnapshots) {
        NormalizedFileSnapshot beforeSnapshot = beforeSnapshots.get(path);
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!fileSnapshot.getSnapshot().isContentAndMetadataUpToDate(beforeSnapshot.getSnapshot())) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        if (afterPreviousSnapshots.containsKey(path)) {
            return true;
        }
        return false;
    }

    private static FileCollectionSnapshot unwrap(FileCollectionSnapshot filesSnapshot) {
        if (filesSnapshot instanceof OutputFilesCollectionSnapshot) {
            return ((OutputFilesCollectionSnapshot) filesSnapshot).getFilesSnapshot();
        }
        return filesSnapshot;
    }
}
