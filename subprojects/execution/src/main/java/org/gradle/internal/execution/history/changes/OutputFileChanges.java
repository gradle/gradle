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

package org.gradle.internal.execution.history.changes;

import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

public class OutputFileChanges implements ChangeContainer {

    private static final CompareStrategy.ChangeFactory<CompleteFileSystemLocationSnapshot> SNAPSHOT_CHANGE_FACTORY = new CompareStrategy.ChangeFactory<CompleteFileSystemLocationSnapshot>() {
        @Override
        public Change added(String path, String propertyTitle, CompleteFileSystemLocationSnapshot current) {
            return new DescriptiveChange("Output property '%s' file %s has been added.", propertyTitle, path);
        }

        @Override
        public Change removed(String path, String propertyTitle, CompleteFileSystemLocationSnapshot previous) {
            return new DescriptiveChange("Output property '%s' file %s has been removed.", propertyTitle, path);
        }

        @Override
        public Change modified(String path, String propertyTitle, CompleteFileSystemLocationSnapshot previous, CompleteFileSystemLocationSnapshot current) {
            return new DescriptiveChange("Output property '%s' file %s has changed.", propertyTitle, path);
        }
    };

    private static final TrivialChangeDetector.ItemComparator<CompleteFileSystemLocationSnapshot> SNAPSHOT_COMPARATOR = new TrivialChangeDetector.ItemComparator<CompleteFileSystemLocationSnapshot>() {
        @Override
        public boolean hasSamePath(CompleteFileSystemLocationSnapshot previous, CompleteFileSystemLocationSnapshot current) {
            return true;
        }

        @Override
        public boolean hasSameContent(CompleteFileSystemLocationSnapshot previous, CompleteFileSystemLocationSnapshot current) {
            return previous.isContentUpToDate(current);
        }
    };

    private static final CompareStrategy<FileSystemSnapshot, CompleteFileSystemLocationSnapshot> COMPARE_STRATEGY = new CompareStrategy<>(
        OutputFileChanges::index,
        SnapshotUtil::getRootHashes,
        new TrivialChangeDetector<>(
            SNAPSHOT_COMPARATOR,
            SNAPSHOT_CHANGE_FACTORY,
            new AbsolutePathChangeDetector<>(
                CompleteFileSystemLocationSnapshot::isContentUpToDate,
                SNAPSHOT_CHANGE_FACTORY
            )
        )
    );

    private final SortedMap<String, FileSystemSnapshot> previous;
    private final SortedMap<String, FileSystemSnapshot> current;

    public OutputFileChanges(SortedMap<String, FileSystemSnapshot> previous, SortedMap<String, FileSystemSnapshot> current) {
        this.previous = previous;
        this.current = current;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        return SortedMapDiffUtil.diff(previous, current, new PropertyDiffListener<String, FileSystemSnapshot, FileSystemSnapshot>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileSystemSnapshot previous, FileSystemSnapshot current) {
                return COMPARE_STRATEGY.visitChangesSince(previous, current, property, visitor);
            }
        });
    }

    private static Map<String, CompleteFileSystemLocationSnapshot> index(FileSystemSnapshot snapshot) {
        HashMap<String, CompleteFileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(new RootTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            public SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
                // Ignore missing roots
                if (!(isRoot && snapshot instanceof MissingFileSnapshot)) {
                    index.put(snapshot.getAbsolutePath(), snapshot);
                }
                return SnapshotVisitResult.CONTINUE;
            }
        });
        return index;
    }
}
