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

package org.gradle.internal.file.fingerprint.classpath;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.PathNormalizationStrategy;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.file.content.hasher.ResourceHasher;
import org.gradle.internal.file.content.hasher.ResourceSnapshotterCacheService;
import org.gradle.internal.file.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.file.fingerprint.fingerprinter.AbstractFileCollectionFingerprinter;
import org.gradle.internal.file.mirror.FileSystemSnapshotter;
import org.gradle.normalization.internal.InputNormalizationStrategy;

import static org.gradle.internal.file.fingerprint.classpath.ClasspathFingerprintingStrategy.NonJarFingerprintingStrategy.USE_FILE_HASH;

public class DefaultClasspathFingerprinter extends AbstractFileCollectionFingerprinter implements ClasspathFingerprinter {
    private final ResourceSnapshotterCacheService cacheService;

    public DefaultClasspathFingerprinter(ResourceSnapshotterCacheService cacheService, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
        this.cacheService = cacheService;
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return ClasspathNormalizer.class;
    }

    @Override
    public FileCollectionFingerprint snapshot(FileCollection files, PathNormalizationStrategy pathNormalizationStrategy, InputNormalizationStrategy inputNormalizationStrategy) {
        ResourceHasher classpathResourceHasher = inputNormalizationStrategy.getRuntimeClasspathNormalizationStrategy().getRuntimeClasspathResourceHasher();
        return super.snapshot(files, new ClasspathFingerprintingStrategy(USE_FILE_HASH, classpathResourceHasher, cacheService));
    }
}
