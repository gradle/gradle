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

import org.gradle.internal.file.FileType;

/**
 * A snapshot where we know the metadata (i.e. the type).
 */
public interface MetadataSnapshot {

    MetadataSnapshot DIRECTORY = new MetadataSnapshot() {
        @Override
        public FileType getType() {
            return FileType.Directory;
        }

        @Override
        public FileSystemNode asFileSystemNode() {
            return PartialDirectoryNode.withoutKnownChildren();
        }
    };

    /**
     * The type of the file.
     */
    FileType getType();

    FileSystemNode asFileSystemNode();
}
