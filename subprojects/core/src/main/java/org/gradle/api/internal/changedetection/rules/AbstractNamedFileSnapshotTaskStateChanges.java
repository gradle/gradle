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

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.Nullable;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.FilesSnapshotSet;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.tasks.TaskFilePropertySpecInternal;
import org.gradle.util.ChangeListener;
import org.gradle.util.DiffUtil;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractNamedFileSnapshotTaskStateChanges implements TaskStateChanges, FilesSnapshotSet {
    private final PreCheckSet preChecksBefore;
    private Map<String, FileCollectionSnapshot> fileSnapshotsBeforeExecution;
    private final String taskName;
    private final boolean allowSnapshotReuse;
    private final String title;
    protected final Collection<? extends TaskFilePropertySpecInternal> fileProperties;
    private final boolean noChanges;
    private final FileCollectionSnapshotter snapshotter;
    protected final TaskExecution previous;
    protected final TaskExecution current;

    protected AbstractNamedFileSnapshotTaskStateChanges(String taskName, TaskExecution previous, TaskExecution current, FileCollectionSnapshotter snapshotter, boolean allowSnapshotReuse, String title, Collection<? extends TaskFilePropertySpecInternal> fileProperties) {
        this.taskName = taskName;
        this.previous = previous;
        this.current = current;
        this.snapshotter = snapshotter;
        this.allowSnapshotReuse = allowSnapshotReuse;
        this.title = title;
        this.fileProperties = fileProperties;
        this.preChecksBefore = buildPreCheckSet(taskName, snapshotter, allowSnapshotReuse, title, fileProperties);
        this.noChanges = previous != null
            && getPreviousPreCheckHash() != null
            && getPreviousPreCheckHash() == getPreCheckHash();
    }

    protected PreCheckSet buildPreCheckSet() {
        return buildPreCheckSet(taskName, snapshotter, allowSnapshotReuse, title, fileProperties);
    }

    private static PreCheckSet buildPreCheckSet(String taskName, FileCollectionSnapshotter snapshotter, boolean allowSnapshotReuse, String title, Collection<? extends TaskFilePropertySpecInternal> fileProperties) {
        Hasher hasher = Hashing.md5().newHasher();
        ImmutableSortedMap.Builder<String, FileCollectionSnapshot.PreCheck> builder = ImmutableSortedMap.naturalOrder();
        for (TaskFilePropertySpecInternal fileProperty : fileProperties) {
            String propertyName = fileProperty.getPropertyName();
            FileCollectionSnapshot.PreCheck result;
            try {
                result = snapshotter.preCheck(fileProperty.getPropertyFiles(), allowSnapshotReuse);
            } catch (UncheckedIOException e) {
                throw new UncheckedIOException(String.format("Failed to capture snapshot of %s files for task '%s' property '%s' during up-to-date check.", title.toLowerCase(), taskName, propertyName), e);
            }
            builder.put(propertyName, result);

            hasher.putString(propertyName, Charsets.UTF_8);
            hasher.putInt(result.getHash());
        }
        return new PreCheckSet(builder.build(), hasher.hash());
    }

    protected abstract Set<FileCollectionSnapshot.ChangeFilter> getFileChangeFilters();
    protected abstract Map<String, FileCollectionSnapshot> getPrevious();

    protected abstract void saveCurrent();

    protected FileCollectionSnapshotter getSnapshotter() {
        return snapshotter;
    }

    protected Map<String, FileCollectionSnapshot> getCurrent() {
        if (fileSnapshotsBeforeExecution == null) {
            fileSnapshotsBeforeExecution = buildSnapshots(preChecksBefore);
        }
        return fileSnapshotsBeforeExecution;
    }

    protected Map<String, FileCollectionSnapshot> buildSnapshots(PreCheckSet preCheckSet) {
        return buildSnapshots(taskName, snapshotter, title, preCheckSet);
    }

    private static Map<String, FileCollectionSnapshot> buildSnapshots(String taskName, FileCollectionSnapshotter snapshotter, String title, PreCheckSet preCheckSet) {
        ImmutableMap.Builder<String, FileCollectionSnapshot> builder = ImmutableMap.builder();
        for (Map.Entry<String, FileCollectionSnapshot.PreCheck> entry : preCheckSet.getPreChecks().entrySet()) {
            String propertyName = entry.getKey();
            FileCollectionSnapshot.PreCheck preCheck = entry.getValue();
            FileCollectionSnapshot result;
            try {
                result = snapshotter.snapshot(preCheck);
            } catch (UncheckedIOException e) {
                throw new UncheckedIOException(String.format("Failed to capture snapshot of %s files for task '%s' property '%s' during up-to-date check.", title.toLowerCase(), taskName, propertyName), e);
            }
            builder.put(propertyName, result);
        }
        return builder.build();
    }

    protected HashCode getPreCheckHash() {
        return preChecksBefore.getHash();
    }

    abstract protected HashCode getPreviousPreCheckHash();

    @Override
    public Iterator<TaskStateChange> iterator() {
        if (noChanges) {
            return Iterators.emptyIterator();
        }
        if (getPrevious() == null) {
            return Iterators.<TaskStateChange>singletonIterator(new DescriptiveChange(title + " file history is not available."));
        }
        final List<TaskStateChange> propertyChanges = Lists.newLinkedList();
        DiffUtil.diff(getCurrent().keySet(), getPrevious().keySet(), new ChangeListener<String>() {
            @Override
            public void added(String element) {
                propertyChanges.add(new DescriptiveChange("%s property '%s' has been added for task '%s'", title, element, taskName));
            }

            @Override
            public void removed(String element) {
                propertyChanges.add(new DescriptiveChange("%s property '%s' has been removed for task '%s'", title, element, taskName));
            }

            @Override
            public void changed(String element) {
                // Won't happen
                throw new AssertionError();
            }
        });
        if (!propertyChanges.isEmpty()) {
            return propertyChanges.iterator();
        }
        return Iterators.concat(Iterables.transform(getCurrent().entrySet(), new Function<Map.Entry<String, FileCollectionSnapshot>, Iterator<TaskStateChange>>() {
            @Override
            public Iterator<TaskStateChange> apply(Map.Entry<String, FileCollectionSnapshot> entry) {
                String propertyName = entry.getKey();
                FileCollectionSnapshot currentSnapshot = entry.getValue();
                FileCollectionSnapshot previousSnapshot = getPrevious().get(propertyName);
                String propertyTitle = title + " property '" + propertyName + "'";
                return currentSnapshot.iterateContentChangesSince(previousSnapshot, propertyTitle, getFileChangeFilters());
            }
        }).iterator());
    }

    @Override
    public void snapshotBeforeTask() {
        getCurrent();
    }

    @Override
    public void snapshotAfterTask() {
        saveCurrent();
    }

    public FilesSnapshotSet getUnifiedSnapshot() {
        return this;
    }

    @Nullable
    @Override
    public FileSnapshot findSnapshot(File file) {
        for (FileCollectionSnapshot propertySnapshot : getCurrent().values()) {
            FileSnapshot snapshot = propertySnapshot.getSnapshot().findSnapshot(file);
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    protected static class PreCheckSet {
        private final Map<String, FileCollectionSnapshot.PreCheck> preChecks;
        private final HashCode hashCode;

        public PreCheckSet(Map<String, FileCollectionSnapshot.PreCheck> preChecks, HashCode hashCode) {
            this.preChecks = preChecks;
            this.hashCode = hashCode;
        }

        public Map<String, FileCollectionSnapshot.PreCheck> getPreChecks() {
            return preChecks;
        }

        public HashCode getHash() {
            return hashCode;
        }
    }
}
