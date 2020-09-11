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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class WatchedHierarchies {
    private Set<Path> watchedRoots = new HashSet<>();
    private FileHierarchySet watchedHierarchies = DefaultFileHierarchySet.of();

    public boolean contains(Path watchableHierarchy) {
        return watchedHierarchies.contains(watchableHierarchy.toString());
    }

    public Set<Path> getWatchedRoots() {
        return watchedRoots;
    }

    public void updateWatchedHierarchies(WatchableHierarchies watchableHierarchies, SnapshotHierarchy vfsRoot) {
        watchedHierarchies = DefaultFileHierarchySet.of();
        Stream<Path> hierarchiesWithSnapshots = watchableHierarchies.getWatchableHierarchies().stream()
            .flatMap(watchableHierarchy -> {
                if (watchedHierarchies.contains(watchableHierarchy.toString())) {
                    return Stream.empty();
                }
                CheckIfNonEmptySnapshotVisitor checkIfNonEmptySnapshotVisitor = new CheckIfNonEmptySnapshotVisitor(watchableHierarchies);
                vfsRoot.visitSnapshotRoots(watchableHierarchy.toString(), new FilterAlreadyCoveredSnapshotsVisitor(checkIfNonEmptySnapshotVisitor, watchedHierarchies));
                if (checkIfNonEmptySnapshotVisitor.isEmpty()) {
                    return Stream.empty();
                }
                watchedHierarchies = watchedHierarchies.plus(watchableHierarchy.toFile());
                return checkIfNonEmptySnapshotVisitor.containsOnlyMissingFiles()
                    ? Stream.of(locationOrFirstExistingAncestor(watchableHierarchy))
                    : Stream.of(watchableHierarchy);
            });

        watchedRoots = resolveHierarchiesToWatch(hierarchiesWithSnapshots);
    }

    private Path locationOrFirstExistingAncestor(Path watchableHierarchy) {
        if (Files.isDirectory(watchableHierarchy)) {
            return watchableHierarchy;
        }
        return SnapshotWatchedDirectoryFinder.findFirstExistingAncestor(watchableHierarchy);
    }

    /**
     * Filters out directories whose ancestor is also among the watched directories.
     */
    @VisibleForTesting
    static Set<Path> resolveHierarchiesToWatch(Stream<Path> directories) {
        Set<Path> hierarchies = new HashSet<>();
        directories
            .sorted(Comparator.comparingInt(Path::getNameCount))
            .filter(path -> {
                Path parent = path;
                while (true) {
                    parent = parent.getParent();
                    if (parent == null) {
                        break;
                    }
                    if (hierarchies.contains(parent)) {
                        return false;
                    }
                }
                return true;
            })
            .forEach(hierarchies::add);
        return hierarchies;
    }

    private static class FilterAlreadyCoveredSnapshotsVisitor implements SnapshotHierarchy.SnapshotVisitor {
        private final SnapshotHierarchy.SnapshotVisitor delegate;
        private final FileHierarchySet alreadyCoveredSnapshots;

        public FilterAlreadyCoveredSnapshotsVisitor(SnapshotHierarchy.SnapshotVisitor delegate, FileHierarchySet alreadyCoveredSnapshots) {
            this.delegate = delegate;
            this.alreadyCoveredSnapshots = alreadyCoveredSnapshots;
        }

        @Override
        public void visitSnapshotRoot(CompleteFileSystemLocationSnapshot snapshot) {
            if (!alreadyCoveredSnapshots.contains(snapshot.getAbsolutePath())) {
                delegate.visitSnapshotRoot(snapshot);
            }
        }
    }
}
