/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.internal.file.FileMetadata;

import javax.annotation.Nullable;

public interface DirectorySnapshotBuilder {

    void enterDirectory(DirectorySnapshot directorySnapshot, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy);

    void enterDirectory(FileMetadata.AccessType accessType, String absolutePath, String name, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy);

    void visitLeafElement(FileSystemLeafSnapshot snapshot);

    void visitDirectory(DirectorySnapshot directorySnapshot);

    @Nullable
    FileSystemLocationSnapshot leaveDirectory();

    @Nullable
    FileSystemLocationSnapshot getResult();

    enum EmptyDirectoryHandlingStrategy {
        INCLUDE_EMPTY_DIRS,
        EXCLUDE_EMPTY_DIRS
    }
}
