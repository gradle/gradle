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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.*;

class FileCollectionSnapshotImpl implements FileCollectionSnapshot {
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

    public FilesSnapshotSet getSnapshot() {
        return new FilesSnapshotSet() {
            public FileSnapshot findSnapshot(File file) {
                IncrementalFileSnapshot s = snapshots.get(file.getAbsolutePath());
                if (s instanceof FileSnapshot) {
                    return (FileSnapshot) s;
                }
                return null;
            }
        };
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
    public ChangeIterator<String> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, final Set<ChangeFilter> filters) {
        final Map<String, IncrementalFileSnapshot> otherSnapshots = new HashMap<String, IncrementalFileSnapshot>(oldSnapshot.getSnapshots());
        final Iterator<String> currentFiles = snapshots.keySet().iterator();
        final boolean includeAdded = !filters.contains(ChangeFilter.IgnoreAddedFiles);

        return new ChangeIterator<String>() {
            private Iterator<String> removedFiles;

            public boolean next(ChangeListener<String> listener) {
                while (currentFiles.hasNext()) {
                    String currentFile = currentFiles.next();
                    IncrementalFileSnapshot otherFile = otherSnapshots.remove(currentFile);
                    if (otherFile == null) {
                        if (includeAdded) {
                            listener.added(currentFile);
                            return true;
                        }
                    } else if (!snapshots.get(currentFile).isContentUpToDate(otherFile)) {
                        listener.changed(currentFile);
                        return true;
                    }
                }

                // Create a single iterator to use for all of the removed files
                if (removedFiles == null) {
                    removedFiles = otherSnapshots.keySet().iterator();
                }

                if (removedFiles.hasNext()) {
                    listener.removed(removedFiles.next());
                    return true;
                }

                return false;
            }
        };
    }

}
