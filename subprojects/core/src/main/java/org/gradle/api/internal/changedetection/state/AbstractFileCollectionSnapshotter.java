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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.cache.CacheAccess;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareType.UNORDERED;

abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    protected final FileSnapshotter snapshotter;
    protected final StringInterner stringInterner;
    protected final FileResolver fileResolver;
    protected CacheAccess cacheAccess;

    public AbstractFileCollectionSnapshotter(FileSnapshotter snapshotter, CacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
        this.fileResolver = fileResolver;
    }

    @Override
    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(Collections.<String, IncrementalFileSnapshot>emptyMap(), UNORDERED);
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(FileCollectionSnapshotImpl.class, new DefaultFileSnapshotterSerializer(stringInterner));
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareType compareType) {
        final List<FileTreeElement> fileTreeElements = Lists.newLinkedList();
        final List<File> missingFiles = Lists.newArrayList();
        visitFiles(input, fileTreeElements, missingFiles);

        if (fileTreeElements.isEmpty() && missingFiles.isEmpty()) {
            return emptySnapshot();
        }

        final Map<String, IncrementalFileSnapshot> snapshots = new HashMap<String, IncrementalFileSnapshot>();

        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (FileTreeElement fileDetails : fileTreeElements) {
                    String absolutePath = getInternedAbsolutePath(fileDetails.getFile());
                    if (!snapshots.containsKey(absolutePath)) {
                        if (fileDetails.isDirectory()) {
                            snapshots.put(absolutePath, DirSnapshot.getInstance());
                        } else {
                            snapshots.put(absolutePath, new FileHashSnapshot(snapshotter.snapshot(fileDetails).getHash(), fileDetails.getLastModified()));
                        }
                    }
                }
                for (File missingFile : missingFiles) {
                    String absolutePath = getInternedAbsolutePath(missingFile);
                    if (!snapshots.containsKey(absolutePath)) {
                        snapshots.put(absolutePath, MissingFileSnapshot.getInstance());
                    }
                }
            }
        });
        return new FileCollectionSnapshotImpl(snapshots, compareType);
    }

    private String getInternedAbsolutePath(File file) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    abstract protected void visitFiles(FileCollection input, List<FileTreeElement> fileTreeElements, List<File> missingFiles);
}
