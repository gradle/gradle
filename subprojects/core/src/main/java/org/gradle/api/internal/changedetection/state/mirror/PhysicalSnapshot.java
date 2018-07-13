/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror;

import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.Comparator;

/**
 * A snapshot of a concrete file/directory tree.
 *
 * The file is not required to exist (see {@link PhysicalMissingSnapshot}.
 */
public interface PhysicalSnapshot extends FileSystemSnapshot {

    Comparator<PhysicalSnapshot> BY_NAME = new Comparator<PhysicalSnapshot>() {
        @Override
        public int compare(PhysicalSnapshot o1, PhysicalSnapshot o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    /**
     * The type of the file.
     */
    FileType getType();

    /**
     * The file name.
     */
    String getName();

    /**
     * The absolute path of the file.
     */
    String getAbsolutePath();

    /**
     * The content hash of the snapshot.
     */
    HashCode getContentHash();

    /**
     * Whether the content and the metadata (modification date) of the current snapshot is the same as for the given one.
     */
    boolean isContentAndMetadataUpToDate(PhysicalSnapshot other);
}
