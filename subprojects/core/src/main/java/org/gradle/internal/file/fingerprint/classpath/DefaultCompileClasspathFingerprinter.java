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
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.file.content.hasher.CachingResourceHasher;
import org.gradle.internal.file.content.hasher.ResourceHasher;
import org.gradle.internal.file.content.hasher.ResourceSnapshotterCacheService;
import org.gradle.internal.file.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.file.fingerprint.fingerprinter.AbstractFileCollectionFingerprinter;
import org.gradle.internal.file.mirror.FileSystemSnapshotter;
import org.gradle.normalization.internal.InputNormalizationStrategy;

import static org.gradle.internal.file.fingerprint.classpath.ClasspathFingerprintingStrategy.NonJarFingerprintingStrategy.IGNORE;

public class DefaultCompileClasspathFingerprinter extends AbstractFileCollectionFingerprinter implements CompileClasspathFingerprinter {
    private final ResourceHasher classpathResourceHasher;
    private final ResourceSnapshotterCacheService cacheService;

    public DefaultCompileClasspathFingerprinter(ResourceSnapshotterCacheService cacheService, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
        this.cacheService = cacheService;
        this.classpathResourceHasher = new CachingResourceHasher(new AbiExtractingClasspathResourceHasher(), cacheService);
    }

    @Override
    public FileCollectionFingerprint fingerprint(FileCollection files, PathNormalizationStrategy pathNormalizationStrategy, InputNormalizationStrategy inputNormalizationStrategy) {
        return super.fingerprint(
            files,
            new ClasspathFingerprintingStrategy(IGNORE, classpathResourceHasher, cacheService));
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return CompileClasspathNormalizer.class;
    }
}
