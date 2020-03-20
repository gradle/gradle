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

package org.gradle.internal.vfs.watch;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WatchRootUtil {
    /**
     * Filters out directories whose ancestor is also among the watched directories.
     */
    public static Set<Path> resolveRootsToWatch(Set<String> directories) {
        Set<Path> roots = new HashSet<>();
        directories.stream()
            .map(Paths::get)
            .sorted(Comparator.comparingInt(Path::getNameCount))
            .filter(path -> {
                Path parent = path;
                while (true) {
                    parent = parent.getParent();
                    if (parent == null) {
                        break;
                    }
                    if (roots.contains(parent)) {
                        return false;
                    }
                }
                return true;
            })
            .forEach(roots::add);
        return roots;
    }

    /**
     * Filters out directories whose ancestor is also among the watched directories.
     */
    public static Set<Path> resolveRootsToWatchFromPaths(Set<Path> directories) {
        Set<Path> roots = new HashSet<>();
        directories.stream()
            .sorted(Comparator.comparingInt(Path::getNameCount))
            .filter(path -> {
                Path parent = path;
                while (true) {
                    parent = parent.getParent();
                    if (parent == null) {
                        break;
                    }
                    if (roots.contains(parent)) {
                        return false;
                    }
                }
                return true;
            })
            .forEach(roots::add);
        return roots;
    }

    /**
     * Resolves the directories to watch from a snapshot hierarchy - the current state of the VFS.
     *
     * The directories to watch are
     * - roots of completed directory snapshots
     * - parents of complete snapshots
     * - the first existing parent directory for a missing file snapshot
     *
     * @param watchFilter returns true for paths which shouldn't be watched
     * @param mustWatchDirectories directories which always should be watched. Will be part of the result.
     */
    public static Set<String> resolveDirectoriesToWatch(CompleteFileSystemLocationSnapshot root, Predicate<String> watchFilter, Collection<Path> mustWatchDirectories) {
        Set<String> watchedDirectories = mustWatchDirectories.stream()
            .map(Path::toString)
            .collect(Collectors.toSet());

        Path path = Paths.get(root.getAbsolutePath());

        // For existing files and directories we watch the parent directory,
        // so we learn if the entry itself disappears or gets modified.
        // In case of a missing file we need to find the closest existing
        // ancestor to watch so we can learn if the missing file respawns.
        Path ancestorToWatch;
        switch (root.getType()) {
            case RegularFile:
            case Directory:
                ancestorToWatch = path.getParent();
                break;
            case Missing:
                ancestorToWatch = findFirstExistingAncestor(path);
                break;
            default:
                throw new AssertionError();
        }
        watchedDirectories.add(ancestorToWatch.toString());
        root.accept(new FileSystemSnapshotVisitor() {
            @Override
            public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                // We don't watch things that shouldn't be watched
                if (!watchFilter.test(directorySnapshot.getAbsolutePath())) {
                    return false;
                }
                // For directory entries we watch the directory itself,
                // so we learn about new children spawning. If the directory
                // has children, it would be watched through them already.
                // This is here to make sure we also watch empty directories.
                watchedDirectories.add(directorySnapshot.getAbsolutePath());
                return true;
            }

            @Override
            public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
            }

            @Override
            public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {

            }
        });
        return watchedDirectories;
    }

    public static ImmutableList<Path> getDirectoriesToWatch(CompleteFileSystemLocationSnapshot snapshot) {
        Path path = Paths.get(snapshot.getAbsolutePath());

        // For existing files and directories we watch the parent directory,
        // so we learn if the entry itself disappears or gets modified.
        // In case of a missing file we need to find the closest existing
        // ancestor to watch so we can learn if the missing file respawns.
        Path ancestorToWatch;
        switch (snapshot.getType()) {
            case RegularFile:
            case Directory:
                ancestorToWatch = path.getParent();
                break;
            case Missing:
                ancestorToWatch = findFirstExistingAncestor(path);
                break;
            default:
                throw new AssertionError();
        }
        return snapshot.getType() == FileType.Directory
            ? ImmutableList.of(ancestorToWatch, path)
            : ImmutableList.of(ancestorToWatch);

    }

    private static Path findFirstExistingAncestor(Path path) {
        Path candidate = path;
        while (true) {
            candidate = candidate.getParent();
            if (candidate == null) {
                // TODO Can this happen on Windows when a SUBST'd drive is unregistered?
                throw new IllegalStateException("Couldn't find existing ancestor for " + path);
            }
            // TODO Use the VFS to find the ancestor instead
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
    }
}
