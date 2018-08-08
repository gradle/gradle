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

import java.util.List;

/**
 * A file snapshot which can have children (i.e. a directory).
 */
public class PhysicalDirectorySnapshot extends AbstractPhysicalSnapshot implements PhysicalSnapshot {
    private final List<PhysicalSnapshot> children;
    private final HashCode contentHash;

    public PhysicalDirectorySnapshot(String absolutePath, String name, List<PhysicalSnapshot> children, HashCode contentHash) {
        super(absolutePath, name);
        this.children = children;
        this.contentHash = contentHash;
    }

    @Override
    public HashCode getHash() {
        return contentHash;
    }

    @Override
    public FileType getType() {
        return FileType.Directory;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(PhysicalSnapshot other) {
        return other instanceof PhysicalDirectorySnapshot;
    }

    @Override
    public void accept(PhysicalSnapshotVisitor visitor) {
        if (!visitor.preVisitDirectory(this)) {
            return;
        }
        for (PhysicalSnapshot child : children) {
            child.accept(visitor);
        }
        visitor.postVisitDirectory(this);
    }
}
