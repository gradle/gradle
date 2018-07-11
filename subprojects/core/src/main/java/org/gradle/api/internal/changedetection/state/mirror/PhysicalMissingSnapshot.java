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
import org.gradle.internal.hash.Hashing;

/**
 * Represents a missing file.
 */
public class PhysicalMissingSnapshot extends AbstractPhysicalSnapshot {

    public static final HashCode SIGNATURE = Hashing.md5().hashString(PhysicalMissingSnapshot.class.getName());

    public PhysicalMissingSnapshot(String absolutePath, String name) {
        super(absolutePath, name);
    }

    @Override
    public FileType getType() {
        return FileType.Missing;
    }

    @Override
    public HashCode getContentHash() {
        return SIGNATURE;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(PhysicalSnapshot other) {
        return other instanceof PhysicalMissingSnapshot;
    }

    @Override
    public void accept(PhysicalSnapshotVisitor visitor) {
        visitor.visit(this);
    }
}
