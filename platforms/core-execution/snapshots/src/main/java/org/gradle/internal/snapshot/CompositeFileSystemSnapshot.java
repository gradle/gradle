/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class CompositeFileSystemSnapshot implements FileSystemSnapshot {
    private final ImmutableList<FileSystemSnapshot> snapshots;

    private CompositeFileSystemSnapshot(Collection<? extends FileSystemSnapshot> snapshots) {
        this.snapshots = ImmutableList.copyOf(snapshots);
    }

    public static FileSystemSnapshot of(List<? extends FileSystemSnapshot> snapshots) {
        switch (snapshots.size()) {
            case 0:
                return EMPTY;
            case 1:
                return snapshots.get(0);
            default:
                return new CompositeFileSystemSnapshot(snapshots);
        }
    }

    @Override
    public Stream<FileSystemLocationSnapshot> roots() {
        return snapshots.stream()
            .flatMap(FileSystemSnapshot::roots);
    }

    @Override
    public SnapshotVisitResult accept(FileSystemSnapshotHierarchyVisitor visitor) {
        for (FileSystemSnapshot snapshot : snapshots) {
            SnapshotVisitResult result = snapshot.accept(visitor);
            if (result == SnapshotVisitResult.TERMINATE) {
                return SnapshotVisitResult.TERMINATE;
            }
        }
        return SnapshotVisitResult.CONTINUE;
    }

    @Override
    public SnapshotVisitResult accept(RelativePathTracker pathTracker, RelativePathTrackingFileSystemSnapshotHierarchyVisitor visitor) {
        for (FileSystemSnapshot snapshot : snapshots) {
            SnapshotVisitResult result = snapshot.accept(pathTracker, visitor);
            if (result == SnapshotVisitResult.TERMINATE) {
                return SnapshotVisitResult.TERMINATE;
            }
        }
        return SnapshotVisitResult.CONTINUE;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CompositeFileSystemSnapshot that = (CompositeFileSystemSnapshot) o;

        return snapshots.equals(that.snapshots);
    }

    @Override
    public int hashCode() {
        return snapshots.hashCode();
    }

    @Override
    public String toString() {
        return snapshots.toString();
    }
}
