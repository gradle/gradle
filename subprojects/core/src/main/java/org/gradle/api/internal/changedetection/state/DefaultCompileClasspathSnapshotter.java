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

import com.google.common.hash.HashCode;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.resources.normalization.ResourceNormalizationHandler;
import org.gradle.cache.PersistentIndexedCache;

public class DefaultCompileClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    private final ContentHasher classpathContentHasher;
    private final ContentHasher jarContentHasher;

    public DefaultCompileClasspathSnapshotter(PersistentIndexedCache<HashCode, HashCode> classCache, PersistentIndexedCache<HashCode, HashCode> jarCache, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
        AbiExtractingClasspathContentHasher abiExtractingClasspathContentHasher = new AbiExtractingClasspathContentHasher();
        this.classpathContentHasher = new CachingContentHasher(abiExtractingClasspathContentHasher, classCache);
        this.jarContentHasher = new CachingContentHasher(new JarContentHasher(abiExtractingClasspathContentHasher, stringInterner), jarCache);
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection files, TaskFilePropertyCompareStrategy compareStrategy, SnapshotNormalizationStrategy snapshotNormalizationStrategy, ResourceNormalizationHandler normalizationHandler) {
        return super.snapshot(
            files,
            new CompileClasspathSnapshotBuilder(classpathContentHasher, jarContentHasher, getStringInterner()));
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return CompileClasspathSnapshotter.class;
    }
}
