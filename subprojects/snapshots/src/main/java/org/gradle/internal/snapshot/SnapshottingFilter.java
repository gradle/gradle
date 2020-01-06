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

import java.nio.file.Path;

public interface SnapshottingFilter {
    SnapshottingFilter EMPTY = new SnapshottingFilter() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public FileSystemSnapshotPredicate getAsSnapshotPredicate() {
            return (location, relativePath) -> true;
        }

        @Override
        public DirectoryWalkerPredicate getAsDirectoryWalkerPredicate() {
            return (path, name, isDirectory, relativePath) -> true;
        }
    };

    boolean isEmpty();
    FileSystemSnapshotPredicate getAsSnapshotPredicate();
    DirectoryWalkerPredicate getAsDirectoryWalkerPredicate();

    interface DirectoryWalkerPredicate {
        boolean test(Path path, String name, boolean isDirectory, Iterable<String> relativePath);
    }

    interface FileSystemSnapshotPredicate {
        boolean test(CompleteFileSystemLocationSnapshot fileSystemLocation, Iterable<String> relativePath);
    }
}
