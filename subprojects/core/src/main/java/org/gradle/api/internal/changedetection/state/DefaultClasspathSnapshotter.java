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
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;

public class DefaultClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements ClasspathSnapshotter {
    private final ContentHasher classpathContentHasher;
    private final ContentHasher jarContentHasher;

    public DefaultClasspathSnapshotter(ContentHasher classpathContentHasher, ContentHasher jarContentHasher, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
        this.classpathContentHasher = classpathContentHasher;
        this.jarContentHasher = jarContentHasher;
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return ClasspathSnapshotter.class;
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection files, TaskFilePropertyCompareStrategy compareStrategy, SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        return super.snapshot(files, new RuntimeClasspathSnapshotBuilder(classpathContentHasher, jarContentHasher, getStringInterner()));
    }
}
