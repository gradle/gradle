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

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import java.io.File;
import java.util.stream.Stream;

public class SnapshotWatchedDirectoryFinder {

    /**
     * Resolves the directories to watch for a snapshot.
     *
     * The directories to watch are
     * - root for a directory snapshot
     * - parent dir for regular file snapshots
     * - the first existing parent directory for a missing file snapshot
     */
    public static Stream<File> getDirectoriesToWatch(FileSystemLocationSnapshot snapshot) {
        File path = new File(snapshot.getAbsolutePath());

        // For existing files and directories we watch the parent directory,
        // so we learn if the entry itself disappears or gets modified.
        // In case of a missing file we need to find the closest existing
        // ancestor to watch so we can learn if the missing file respawns.
        File ancestorToWatch;
        switch (snapshot.getType()) {
            case RegularFile:
                return Stream.of(path.getParentFile());
            case Directory:
                ancestorToWatch = path.getParentFile();
                // If the path already is the root (e.g. C:\ on Windows),
                // then we can't watch its parent.
                return ancestorToWatch == null
                    ? Stream.of(path)
                    : Stream.of(ancestorToWatch, path);
            case Missing:
                return Stream.of(findFirstExistingAncestor(path));
            default:
                throw new AssertionError();
        }
    }

    private static File findFirstExistingAncestor(File path) {
        File candidate = path;
        while (true) {
            candidate = candidate.getParentFile();
            if (candidate == null) {
                // TODO Can this happen on Windows when a SUBST'd drive is unregistered?
                throw new IllegalStateException("Couldn't find existing ancestor for " + path);
            }
            // TODO Use the VFS to find the ancestor instead
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
    }
}
