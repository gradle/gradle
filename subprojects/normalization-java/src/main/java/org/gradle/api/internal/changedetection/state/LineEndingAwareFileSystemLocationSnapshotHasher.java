/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class LineEndingAwareFileSystemLocationSnapshotHasher extends AbstractLineEndingAwareHasher implements FileSystemLocationSnapshotHasher {
    private final FileSystemLocationSnapshotHasher delegate;

    private LineEndingAwareFileSystemLocationSnapshotHasher(FileSystemLocationSnapshotHasher delegate) {
        this.delegate = delegate;
    }

    public static FileSystemLocationSnapshotHasher wrap(FileSystemLocationSnapshotHasher delegate, LineEndingSensitivity lineEndingSensitivity) {
        switch (lineEndingSensitivity) {
            case DEFAULT:
                return delegate;
            case NORMALIZE_LINE_ENDINGS:
                return new LineEndingAwareFileSystemLocationSnapshotHasher(delegate);
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        hasher.putString(getClass().getName());
    }

    @Nullable
    @Override
    public HashCode hash(FileSystemLocationSnapshot snapshot) throws IOException {
        // We have to use rethrow() in order to handle the IOException thrown from delegate.hash()
        return hashContent(snapshot)
            .orElseGet(Suppliers.rethrow(() -> delegate.hash(snapshot)));
    }

    private Optional<HashCode> hashContent(FileSystemLocationSnapshot snapshot) throws IOException {
        return snapshot.getType() == FileType.RegularFile ? hashContent(new File(snapshot.getAbsolutePath())) : Optional.empty();
    }
}
