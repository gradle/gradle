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

public class SnapshotWatchedDirectoryFinder {

    /**
     * Resolves the directories to watch for a snapshot.
     *
     * For existing files, we watch the parent directory,
     * so we learn if the file itself disappears or gets modified.
     * For directories, we only watch the directory itself, as we get
     * events for that.
     * In case of a missing file we need to find the closest existing
     * ancestor to watch so we can learn if the missing file respawns.
     */
    public static File getDirectoryToWatch(FileSystemLocationSnapshot snapshot) {
        File path = new File(snapshot.getAbsolutePath());

        switch (snapshot.getType()) {
            case RegularFile:
                return path.getParentFile();
            case Directory:
                return path;
            case Missing:
                return findFirstExistingAncestor(path);
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
