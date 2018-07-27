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

/**
 * A file snapshot for a regular file.
 */
public class PhysicalFileSnapshot extends AbstractPhysicalSnapshot {
    private final HashCode contentHash;
    private final long lastModified;

    public PhysicalFileSnapshot(String absolutePath, String name, HashCode contentHash, long lastModified) {
        super(absolutePath, name);
        this.contentHash = contentHash;
        this.lastModified = lastModified;
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    @Override
    public HashCode getHash() {
        return contentHash;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(PhysicalSnapshot other) {
        if (!(other instanceof PhysicalFileSnapshot)) {
            return false;
        }
        PhysicalFileSnapshot otherSnapshot = (PhysicalFileSnapshot) other;
        return lastModified == otherSnapshot.lastModified && contentHash.equals(otherSnapshot.contentHash);
    }

    @Override
    public void accept(PhysicalSnapshotVisitor visitor) {
        visitor.visit(this);
    }
}
