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

import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/**
 * A {@link ResourceHasher} that ignores line endings while hashing the file.  It detects whether a file is text or binary and only
 * normalizes line endings for text files.  If a file is detected to be binary, we fall back to the existing non-normalized hash.
 */
public class LineEndingAwareResourceHasher extends AbstractLineEndingAwareHasher implements ResourceHasher {
    private final ResourceHasher delegate;

    private LineEndingAwareResourceHasher(ResourceHasher delegate) {
        this.delegate = delegate;
    }

    public static ResourceHasher wrap(ResourceHasher delegate, LineEndingSensitivity lineEndingSensitivity) {
        switch (lineEndingSensitivity) {
            case DEFAULT:
                return delegate;
            case NORMALIZE_LINE_ENDINGS:
                return new LineEndingAwareResourceHasher(delegate);
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
    public HashCode hash(RegularFileSnapshotContext snapshotContext) throws IOException {
        // We have to use rethrow() in order to handle the IOException thrown from delegate.hash()
        return hashContent(new File(snapshotContext.getSnapshot().getAbsolutePath()))
            .orElseGet(Suppliers.rethrow(() -> delegate.hash(snapshotContext)));
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        // We have to use rethrow() in order to handle the IOException thrown from delegate.hash()
        return hashContent(zipEntryContext)
            .orElseGet(Suppliers.rethrow(() -> delegate.hash(zipEntryContext)));
    }
}
