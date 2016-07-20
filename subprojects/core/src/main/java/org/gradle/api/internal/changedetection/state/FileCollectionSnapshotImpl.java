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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

class FileCollectionSnapshotImpl implements FileCollectionSnapshot, FilesSnapshotSet {
    final Map<String, IncrementalFileSnapshot> snapshots;
    final List<TreeSnapshot> treeSnapshots;

    public FileCollectionSnapshotImpl(List<TreeSnapshot> treeSnapshots) {
        this.treeSnapshots = ImmutableList.copyOf(treeSnapshots);
        this.snapshots = new HashMap<String, IncrementalFileSnapshot>();
        for(TreeSnapshot treeSnapshot : treeSnapshots) {
            addSnapshots(treeSnapshot.getFileSnapshots());
        }
    }

    public FileCollectionSnapshotImpl(Map<String, IncrementalFileSnapshot> snapshots) {
        this.snapshots = snapshots;
        this.treeSnapshots = null;
    }


    private void addSnapshots(Collection<FileSnapshotWithKey> fileSnapshots) {
        for(FileSnapshotWithKey fileSnapshotWithKey : fileSnapshots) {
            snapshots.put(fileSnapshotWithKey.getKey(), fileSnapshotWithKey.getIncrementalFileSnapshot());
        }
    }

    public List<File> getFiles() {
        List<File> files = Lists.newArrayList();
        for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
            if (!(entry.getValue() instanceof DirSnapshot)) {
                files.add(new File(entry.getKey()));
            }
        }
        return files;
    }

    @Override
    public Map<String, IncrementalFileSnapshot> getSnapshots() {
        return snapshots;
    }

    @Nullable
    @Override
    public FileSnapshot findSnapshot(File file) {
        IncrementalFileSnapshot s = snapshots.get(file.getAbsolutePath());
        if (s instanceof FileHashSnapshot) {
            return s;
        }
        return null;
    }

    @Override
    public FilesSnapshotSet getSnapshot() {
        return this;
    }

    @Override
    public Collection<Long> getTreeSnapshotIds() {
        List<Long> snapshotIds = new ArrayList<Long>();
        if (treeSnapshots != null) {
            for (TreeSnapshot treeSnapshot : treeSnapshots) {
                if (treeSnapshot.isShareable() && treeSnapshot.getAssignedId() != null && treeSnapshot.getAssignedId().longValue() != -1) {
                    snapshotIds.add(treeSnapshot.getAssignedId());
                }
            }
        }
        return snapshotIds;
    }

    @Override
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }


    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, final String fileType, final Set<ChangeFilter> filters) {
        final Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(oldSnapshot.getSnapshots());
        final Iterator<String> currentFiles = snapshots.keySet().iterator();
        final boolean includeAdded = !filters.contains(ChangeFilter.IgnoreAddedFiles);
        return new AbstractIterator<TaskStateChange>() {
            private Iterator<String> removedFiles;

            @Override
            protected TaskStateChange computeNext() {
                while (currentFiles.hasNext()) {
                    String currentFile = currentFiles.next();
                    IncrementalFileSnapshot otherFile = otherSnapshots.remove(currentFile);
                    if (otherFile == null) {
                        if (includeAdded) {
                            return new FileChange(currentFile, ChangeType.ADDED, fileType);
                        }
                    } else if (!snapshots.get(currentFile).isContentUpToDate(otherFile)) {
                        return new FileChange(currentFile, ChangeType.MODIFIED, fileType);
                    }
                }

                // Create a single iterator to use for all of the removed files
                if (removedFiles == null) {
                    removedFiles = otherSnapshots.keySet().iterator();
                }

                if (removedFiles.hasNext()) {
                    return new FileChange(removedFiles.next(), ChangeType.REMOVED, fileType);
                }

                return endOfData();
            }
        };
    }

    @Override
    public void appendToCacheKey(TaskCacheKeyBuilder builder) {
        List<String> keys = Lists.newArrayList(snapshots.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            builder.putString(key);
            IncrementalFileSnapshot snapshot = snapshots.get(key);
            builder.putHashCode(snapshot.getHash());
        }
    }
}
