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

package org.gradle.internal.fingerprint.classpath.impl;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.AbiExtractingClasspathResourceHasher;
import org.gradle.api.internal.changedetection.state.CachingResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.classpath.CompileClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.AbstractFileCollectionFingerprinter;
import org.gradle.internal.snapshot.FileSystemSnapshotter;

public class DefaultCompileClasspathFingerprinter extends AbstractFileCollectionFingerprinter implements CompileClasspathFingerprinter {
    private final ClasspathFingerprintingStrategy fingerprintingStrategy;

    public DefaultCompileClasspathFingerprinter(ResourceSnapshotterCacheService cacheService, FileSystemSnapshotter fileSystemSnapshotter, StringInterner stringInterner) {
        super(stringInterner, fileSystemSnapshotter);
        this.fingerprintingStrategy = ClasspathFingerprintingStrategy.compileClasspath(
            new CachingResourceHasher(new AbiExtractingClasspathResourceHasher(), cacheService),
            cacheService,
            stringInterner
        );
    }

    @Override
    public CurrentFileCollectionFingerprint fingerprint(FileCollection files) {
        return super.fingerprint(files, fingerprintingStrategy);
    }

    @Override
    public CurrentFileCollectionFingerprint empty() {
        return fingerprintingStrategy.getEmptyFingerprint();
    }

    @Override
    public Class<? extends FileNormalizer> getRegisteredType() {
        return CompileClasspathNormalizer.class;
    }
}
