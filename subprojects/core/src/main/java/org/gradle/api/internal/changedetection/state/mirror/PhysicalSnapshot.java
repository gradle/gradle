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

import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.internal.file.FileType;

/**
 * A snapshot of a file/directory tree.
 *
 * The file is not required to exist (see {@link PhysicalMissingSnapshot}.
 */
public interface PhysicalSnapshot {

    /**
     * An empty tree.
     *
     * Path and name are not available, since we don't know where the root is.
     */
    PhysicalSnapshot EMPTY = new PhysicalSnapshot() {
        @Override
        public FileType getType() {
            return FileType.Missing;
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAbsolutePath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileContentSnapshot getContent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(PhysicalSnapshotVisitor visitor) {
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
    FileContentSnapshot getContent();

    /**
     * Walks the whole hierarchy represented by this snapshot.
     *
     * The walk is depth first.
     */
    void accept(PhysicalSnapshotVisitor visitor);
}
