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
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;

public class BrokenLinkSnapshot extends AbstractFileSystemLocationSnapshot {
    private static final HashCode SIGNATURE = Hashing.signature(BrokenLinkSnapshot.class);

    public BrokenLinkSnapshot(String absolutePath, String name) {
        super(absolutePath, name);
    }

    @Override
    public FileType getType() {
        return FileType.Missing;
    }

    @Override
    public HashCode getHash() {
        return SIGNATURE;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(FileSystemLocationSnapshot other) {
        return other instanceof BrokenLinkSnapshot;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        visitor.visit(this);
    }
}
