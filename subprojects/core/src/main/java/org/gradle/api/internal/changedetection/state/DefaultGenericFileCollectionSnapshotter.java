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
import org.gradle.api.internal.changedetection.state.mirror.logical.AbsolutePathFileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.logical.NameOnlyPathFileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.logical.NonePathFileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.logical.RelativePathFileCollectionSnapshotBuilder;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.tasks.GenericFileNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.normalization.internal.InputNormalizationStrategy;

public class DefaultGenericFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter implements GenericFileCollectionSnapshotter {
    public DefaultGenericFileCollectionSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return GenericFileNormalizer.class;
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection files, PathNormalizationStrategy pathNormalizationStrategy, InputNormalizationStrategy inputNormalizationStrategy) {
        if (pathNormalizationStrategy == OutputPathNormalizationStrategy.getInstance() || pathNormalizationStrategy == InputPathNormalizationStrategy.ABSOLUTE) {
            return super.snapshot(files, new AbsolutePathFileCollectionSnapshotBuilder());
        } else if (pathNormalizationStrategy == InputPathNormalizationStrategy.RELATIVE) {
            return super.snapshot(files, new RelativePathFileCollectionSnapshotBuilder());
        } else if (pathNormalizationStrategy == InputPathNormalizationStrategy.NAME_ONLY) {
            return super.snapshot(files, new NameOnlyPathFileCollectionSnapshotBuilder());
        } else if (pathNormalizationStrategy == InputPathNormalizationStrategy.NONE) {
            return super.snapshot(files, new NonePathFileCollectionSnapshotBuilder());
        }
        throw new IllegalArgumentException("Unknown normalization strategy " + pathNormalizationStrategy);
    }
}
