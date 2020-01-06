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

public class CompositeFileSystemSnapshot implements FileSystemSnapshot {
    private final ImmutableList<FileSystemSnapshot> snapshots;

    private CompositeFileSystemSnapshot(Collection<FileSystemSnapshot> snapshots) {
        this.snapshots = ImmutableList.copyOf(snapshots);
    }

    public static FileSystemSnapshot of(Collection<FileSystemSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return EMPTY;
        }
        return new CompositeFileSystemSnapshot(snapshots);
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        for (FileSystemSnapshot snapshot : snapshots) {
            snapshot.accept(visitor);
        }
    }
}
