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

import org.gradle.internal.fingerprint.LineEndingNormalization;
import org.gradle.internal.fingerprint.hashing.NormalizedContentHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;

import javax.annotation.Nullable;
import java.io.IOException;

public class LineEndingAwareClasspathResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;
    private final LineEndingNormalization lineEndingNormalization;
    private final NormalizedContentHasher normalizedContentHasher;

    public LineEndingAwareClasspathResourceHasher(ResourceHasher delegate, LineEndingNormalization lineEndingNormalization, NormalizedContentHasher normalizedContentHasher) {
        this.delegate = delegate;
        this.lineEndingNormalization = lineEndingNormalization;
        this.normalizedContentHasher = normalizedContentHasher;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        hasher.putString(getClass().getName());
        hasher.putString(lineEndingNormalization.name());
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext snapshotContext) {
        return lineEndingNormalization.shouldNormalize(snapshotContext.getSnapshot()) ? normalizedContentHasher.hashContent(snapshotContext.getSnapshot()) : delegate.hash(snapshotContext);
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        return zipEntryContext.getEntry().withInputStream(normalizedContentHasher::hashContent);
    }
}
