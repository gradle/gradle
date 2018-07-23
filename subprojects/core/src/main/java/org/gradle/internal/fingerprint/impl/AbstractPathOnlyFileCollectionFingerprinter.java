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

package org.gradle.internal.fingerprint.impl;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.AbstractFileCollectionFingerprinter;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.mirror.logical.FingerprintingStrategy;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.normalization.internal.InputNormalizationStrategy;

public abstract class AbstractPathOnlyFileCollectionFingerprinter extends AbstractFileCollectionFingerprinter {

    private final FingerprintingStrategy fingerprintingStrategy;

    public AbstractPathOnlyFileCollectionFingerprinter(FingerprintingStrategy fingerprintingStrategy, StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
        this.fingerprintingStrategy = fingerprintingStrategy;
    }

    @Override
    public CurrentFileCollectionFingerprint fingerprint(FileCollection files, InputNormalizationStrategy inputNormalizationStrategy) {
        return super.fingerprint(files, fingerprintingStrategy);
    }
}
