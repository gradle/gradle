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
import org.gradle.internal.hash.FileContentType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.LineEndingNormalizingInputStream;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class LineEndingAwareClasspathResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;
    private final LineEndingSensitivity lineEndingSensitivity;
    private final NormalizedContentInfoCollector collector;

    public LineEndingAwareClasspathResourceHasher(ResourceHasher delegate, LineEndingSensitivity lineEndingSensitivity, StreamHasher streamHasher) {
        this.delegate = delegate;
        this.lineEndingSensitivity = lineEndingSensitivity;
        this.collector = new NormalizedContentInfoCollector(LineEndingNormalizingInputStream::new, streamHasher);
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        hasher.putString(getClass().getName());
        hasher.putString(LineEndingNormalizingInputStream.class.getName());
        hasher.putString(lineEndingSensitivity.name());
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext snapshotContext) {
        return lineEndingSensitivity.isCandidate(snapshotContext.getSnapshot()) ?
            hashContent(snapshotContext.getSnapshot()) :
            delegate.hash(snapshotContext);
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        return lineEndingSensitivity.isCandidate(zipEntryContext) ?
            hashContent(zipEntryContext) :
            delegate.hash(zipEntryContext);
    }

    @Nullable
    private HashCode hashContent(RegularFileSnapshot snapshot) {
        NormalizedContentInfoCollector.NormalizedContentInfo normalizedContentInfo = collector.collect(new File(snapshot.getAbsolutePath()));
        return normalizedContentInfo.getContentType() == FileContentType.TEXT ?
            normalizedContentInfo.getHash() :
            snapshot.getHash();
    }

    @Nullable
    private HashCode hashContent(ZipEntryContext zipEntryContext) throws IOException {
        NormalizedContentInfoCollector.NormalizedContentInfo normalizedContentInfo = zipEntryContext.getEntry().withInputStream(collector::collect);
        return normalizedContentInfo.getContentType() == FileContentType.TEXT ?
            normalizedContentInfo.getHash() :
            delegate.hash(zipEntryContext);
    }
}
