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
    public FileCollectionSnapshot snapshot(FileCollection files, TaskFilePropertyCompareType compareType) {
        return new OutputFilesCollectionSnapshot(getRoots(files), snapshotter.snapshot(files, compareType));
    }

    private Map<String, Boolean> getRoots(FileCollection files) {
        Map<String, Boolean> roots = new HashMap<String, Boolean>();
        for (File file : files.getFiles()) {
            roots.put(stringInterner.intern(file.getAbsolutePath()), file.exists());
        }
        return roots;
    }

    /**
     * Returns a new snapshot that ignores new files between 2 previous snapshots
     */
    public OutputFilesCollectionSnapshot createOutputSnapshot(FileCollectionSnapshot afterPreviousExecution, FileCollectionSnapshot beforeExecution, FileCollectionSnapshot afterExecution, FileCollection roots) {
        FileCollectionSnapshot filesSnapshot;
        Map<String, IncrementalFileSnapshot> afterSnapshots = afterExecution.getSnapshots();
        if (!beforeExecution.getSnapshots().isEmpty() && !afterSnapshots.isEmpty()) {
            Map<String, IncrementalFileSnapshot> beforeSnapshots = beforeExecution.getSnapshots();
            Map<String, IncrementalFileSnapshot> previousSnapshots = afterPreviousExecution != null ? afterPreviousExecution.getSnapshots() : new HashMap<String, IncrementalFileSnapshot>();
            int newEntryCount = 0;
            ImmutableMap.Builder<String, IncrementalFileSnapshot> newEntries = ImmutableMap.builder();

            for (Map.Entry<String, IncrementalFileSnapshot> entry : afterSnapshots.entrySet()) {
                final String path = entry.getKey();
                IncrementalFileSnapshot otherFile = beforeSnapshots.get(path);
                if (otherFile == null
                    || !entry.getValue().isContentAndMetadataUpToDate(otherFile)
                    || previousSnapshots.containsKey(path)) {
                    newEntries.put(entry.getKey(), entry.getValue());
                    newEntryCount++;
                }
            }
            if (newEntryCount == afterSnapshots.size()) {
                filesSnapshot = afterExecution;
            } else {
                filesSnapshot = new DefaultFileCollectionSnapshot(newEntries.build(), TaskFilePropertyCompareType.OUTPUT);
            }
        } else {
            filesSnapshot = afterExecution;
        }
        if (filesSnapshot instanceof OutputFilesCollectionSnapshot) {
            filesSnapshot = ((OutputFilesCollectionSnapshot) filesSnapshot).getFilesSnapshot();
        }
        return new OutputFilesCollectionSnapshot(getRoots(roots), filesSnapshot);
    }
}
