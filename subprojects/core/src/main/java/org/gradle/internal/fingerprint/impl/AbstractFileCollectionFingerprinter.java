/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.execution.FileCollectionFingerprinter;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import javax.annotation.Nullable;

/**
 * Responsible for calculating a {@link FileCollectionFingerprint} for a particular {@link FileCollection}.
 */
@NonNullApi
public abstract class AbstractFileCollectionFingerprinter implements FileCollectionFingerprinter {

    private final FileCollectionSnapshotter fileCollectionSnapshotter;
    private final FingerprintingStrategy fingerprintingStrategy;

    public AbstractFileCollectionFingerprinter(FingerprintingStrategy fingerprintingStrategy, FileCollectionSnapshotter fileCollectionSnapshotter) {
        this.fingerprintingStrategy = fingerprintingStrategy;
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
    }

    @Override
    public CurrentFileCollectionFingerprint fingerprint(FileCollection files) {
        FileCollectionSnapshotter.Result snapshotResult = fileCollectionSnapshotter.snapshot(files);
        return fingerprint(snapshotResult.getSnapshot(), null);
    }

    @Override
    public CurrentFileCollectionFingerprint fingerprint(FileSystemSnapshot snapshot, @Nullable FileCollectionFingerprint previousFingerprint) {
        return DefaultCurrentFileCollectionFingerprint.from(snapshot, fingerprintingStrategy, previousFingerprint);
    }

    @Override
    public CurrentFileCollectionFingerprint empty() {
        return fingerprintingStrategy.getEmptyFingerprint();
    }
}
