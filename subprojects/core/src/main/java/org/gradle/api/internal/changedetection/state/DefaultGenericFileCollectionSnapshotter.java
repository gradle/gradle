/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.logical.AbsolutePathFingerprintingStrategy;
import org.gradle.api.internal.changedetection.state.mirror.logical.CurrentFileCollectionFingerprint;
import org.gradle.api.internal.changedetection.state.mirror.logical.FingerprintingStrategy;
import org.gradle.api.internal.changedetection.state.mirror.logical.IgnoredPathFingerprintingStrategy;
import org.gradle.api.internal.changedetection.state.mirror.logical.NameOnlyFingerprintingStrategy;
import org.gradle.api.internal.changedetection.state.mirror.logical.RelativePathFingerprintingStrategy;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.tasks.GenericFileNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.normalization.internal.InputNormalizationStrategy;

public class DefaultGenericFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter implements GenericFileCollectionSnapshotter {
    private final RelativePathFingerprintingStrategy relativePathFingerprintingStrategy;

    public DefaultGenericFileCollectionSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
        relativePathFingerprintingStrategy = new RelativePathFingerprintingStrategy(stringInterner);
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return GenericFileNormalizer.class;
    }

    @Override
    public CurrentFileCollectionFingerprint snapshot(FileCollection files, PathNormalizationStrategy pathNormalizationStrategy, InputNormalizationStrategy inputNormalizationStrategy) {
        FingerprintingStrategy strategy = determineFingerprintStrategy(pathNormalizationStrategy);
        return super.snapshot(files, strategy);
    }

    private FingerprintingStrategy determineFingerprintStrategy(PathNormalizationStrategy pathNormalizationStrategy) {
        switch (pathNormalizationStrategy) {
            case ABSOLUTE:
                return AbsolutePathFingerprintingStrategy.INCLUDE_MISSING;
            case OUTPUT:
                return AbsolutePathFingerprintingStrategy.IGNORE_MISSING;
            case RELATIVE:
                return relativePathFingerprintingStrategy;
            case NAME_ONLY:
                return NameOnlyFingerprintingStrategy.INSTANCE;
            case NONE:
                return IgnoredPathFingerprintingStrategy.INSTANCE;
            default:
                throw new IllegalArgumentException("Unknown normalization strategy " + pathNormalizationStrategy);
        }
    }
}
