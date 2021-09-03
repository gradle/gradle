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
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.io.File;
import java.util.Set;

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
        FileHierarchySet watchedRoots = DefaultFileHierarchySet.of();
        for (File watchableHierarchy : watchableHierarchies.getWatchableHierarchies()) {
            String watchableHierarchyPath = watchableHierarchy.toString();
            if (watchedHierarchies.contains(watchableHierarchyPath)) {
                continue;
            }
            CheckIfNonEmptySnapshotVisitor checkIfNonEmptySnapshotVisitor = new CheckIfNonEmptySnapshotVisitor(watchableHierarchies);
            vfsRoot.visitSnapshotRoots(watchableHierarchyPath, new FilterAlreadyCoveredSnapshotsVisitor(checkIfNonEmptySnapshotVisitor, watchedHierarchies));
            if (checkIfNonEmptySnapshotVisitor.isEmpty()) {
                continue;
            }
            watchedHierarchies = watchedHierarchies.plus(watchableHierarchy);
            String existingAncestorToWatch = checkIfNonEmptySnapshotVisitor.containsOnlyMissingFiles()
                ? locationOrFirstExistingAncestor(watchableHierarchy).toString()
                : watchableHierarchyPath;
            watchedRoots = watchedRoots.plus(existingAncestorToWatch);
        }
        ImmutableSet.Builder<File> roots = ImmutableSet.builder();
        watchedRoots.visitRoots(root -> roots.add(new File(root)));
        return new WatchedHierarchies(watchedHierarchies, roots.build());
    }

    private static File locationOrFirstExistingAncestor(File watchableHierarchy) {
        if (watchableHierarchy.isDirectory()) {
            return watchableHierarchy;
        }
        return SnapshotWatchedDirectoryFinder.findFirstExistingAncestor(watchableHierarchy);
    }

    private static class FilterAlreadyCoveredSnapshotsVisitor implements SnapshotHierarchy.SnapshotVisitor {
        private final SnapshotHierarchy.SnapshotVisitor delegate;
        private final FileHierarchySet alreadyCoveredSnapshots;

        public FilterAlreadyCoveredSnapshotsVisitor(SnapshotHierarchy.SnapshotVisitor delegate, FileHierarchySet alreadyCoveredSnapshots) {
            this.delegate = delegate;
            this.alreadyCoveredSnapshots = alreadyCoveredSnapshots;
        }

        @Override
        public void visitSnapshotRoot(FileSystemLocationSnapshot snapshot) {
            if (!alreadyCoveredSnapshots.contains(snapshot.getAbsolutePath())) {
                delegate.visitSnapshotRoot(snapshot);
            }
        }
    }
}
