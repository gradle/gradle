/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl;

import com.google.common.collect.EnumMultiset;
import com.google.common.collect.Multiset;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.vfs.FileSystemWatchingStatistics;

import static org.gradle.internal.snapshot.SnapshotVisitResult.CONTINUE;

public class DefaultFileSystemWatchingStatistics implements FileSystemWatchingStatistics {
    private final FileWatcherRegistry.FileWatchingStatistics fileWatchingStatistics;
    private final VirtualFileSystemStatistics vfsStatistics;

    public DefaultFileSystemWatchingStatistics(
        FileWatcherRegistry.FileWatchingStatistics fileWatchingStatistics,
        SnapshotHierarchy vfsRoot
    ) {
        this.fileWatchingStatistics = fileWatchingStatistics;
        this.vfsStatistics = getStatistics(vfsRoot);
    }

    @Override
    public int getNumberOfReceivedEvents() {
        return fileWatchingStatistics.getNumberOfReceivedEvents();
    }

    @Override
    public int getNumberOfWatchedHierarchies() {
        return fileWatchingStatistics.getNumberOfWatchedHierarchies();
    }

    @Override
    public int getRetainedRegularFiles() {
        return vfsStatistics.getRetained(FileType.RegularFile);
    }

    @Override
    public int getRetainedDirectories() {
        return vfsStatistics.getRetained(FileType.Directory);
    }

    @Override
    public int getRetainedMissingFiles() {
        return vfsStatistics.getRetained(FileType.Missing);
    }

    private static VirtualFileSystemStatistics getStatistics(SnapshotHierarchy root) {
        EnumMultiset<FileType> retained = EnumMultiset.create(FileType.class);
        root.rootSnapshots()
            .forEach(snapshot -> snapshot.accept(entrySnapshot -> {
                retained.add(entrySnapshot.getType());
                return CONTINUE;
            }));
        return new VirtualFileSystemStatistics(retained);
    }

    private static class VirtualFileSystemStatistics {
        private final Multiset<FileType> retained;

        public VirtualFileSystemStatistics(Multiset<FileType> retained) {
            this.retained = retained;
        }

        public int getRetained(FileType fileType) {
            return retained.count(fileType);
        }
    }
}
