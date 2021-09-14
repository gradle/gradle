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

package org.gradle.internal.watch.registry.impl;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.io.File;
import java.util.Set;
import java.util.stream.Stream;

public class WatchedHierarchies {
    public static final WatchedHierarchies EMPTY = new WatchedHierarchies(DefaultFileHierarchySet.of(), ImmutableSet.of());

    private final ImmutableSet<File> watchedRoots;
    private final FileHierarchySet watchedHierarchies;

    private WatchedHierarchies(FileHierarchySet watchedHierarchies, ImmutableSet<File> watchedRoots) {
        this.watchedRoots = watchedRoots;
        this.watchedHierarchies = watchedHierarchies;
    }

    public boolean contains(File watchableHierarchy) {
        return watchedHierarchies.contains(watchableHierarchy.getAbsolutePath());
    }

    public Set<File> getWatchedRoots() {
        return watchedRoots;
    }

    public static WatchedHierarchies resolveWatchedHierarchies(WatchableHierarchies watchableHierarchies, SnapshotHierarchy vfsRoot) {
        FileHierarchySet watchedHierarchies = DefaultFileHierarchySet.of();
        for (File watchableHierarchy : watchableHierarchies.getRecentlyUsedHierarchies()) {
            String watchableHierarchyPath = watchableHierarchy.getAbsolutePath();
            if (watchedHierarchies.contains(watchableHierarchyPath)) {
                continue;
            }

            if (hasNoContent(vfsRoot.rootSnapshotsUnder(watchableHierarchyPath), watchableHierarchies, watchedHierarchies)) {
                continue;
            }
            watchedHierarchies = watchedHierarchies.plus(watchableHierarchy);
        }
        ImmutableSet.Builder<File> roots = ImmutableSet.builder();
        watchedHierarchies.visitRoots(root -> roots.add(new File(root)));
        return new WatchedHierarchies(watchedHierarchies, roots.build());
    }

    private static boolean hasNoContent(Stream<FileSystemLocationSnapshot> snapshots, WatchableHierarchies watchableHierarchies, FileHierarchySet watchedHierarchies) {
        return snapshots
            .filter(snapshot -> !watchedHierarchies.contains(snapshot.getAbsolutePath()))
            .allMatch(snapshot -> isMissing(snapshot) || watchableHierarchies.ignoredForWatching(snapshot));
    }

    private static boolean isMissing(FileSystemLocationSnapshot snapshot) {
        // Missing accessed indirectly means we have a dangling symlink in the directory, and that's content we cannot ignore
        return snapshot.getType() == FileType.Missing && snapshot.getAccessType() == FileMetadata.AccessType.DIRECT;
    }
}
