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

import org.gradle.internal.fingerprint.hashing.FileContentHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.io.File;

/**
 * A {@link FileContentHasher} that detects source files and normalizes the line endings
 * in the content.  For files that are not "known" source files, it simply returns the previously
 * calculated snapshot hash.
 */
public class LineEndingAwareFileContentHasher implements FileContentHasher {
    private final FileHasher lineEndingNormalizingHasher;
    private final ResourceSnapshotterCacheService cacheService;
    private final SourceFileFilter sourceFileFilter;
    private final HashCode configurationHashCode;

    public LineEndingAwareFileContentHasher(FileHasher lineEndingNormalizingHasher, ResourceSnapshotterCacheService cacheService, SourceFileFilter sourceFileFilter) {
        this.lineEndingNormalizingHasher = lineEndingNormalizingHasher;
        this.cacheService = cacheService;
        this.sourceFileFilter = sourceFileFilter;
        this.configurationHashCode = getConfigurationHashCode();
    }

    private HashCode getConfigurationHashCode() {
        Hasher hasher = Hashing.newHasher();
        appendConfigurationToHasher(hasher);
        return hasher.hash();
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        sourceFileFilter.appendConfigurationToHasher(hasher);
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshot snapshot) {
        return sourceFileFilter.isSourceFile(snapshot.getAbsolutePath())
            ? cacheService.hashFile(snapshot, s -> lineEndingNormalizingHasher.hash(new File(snapshot.getAbsolutePath())), configurationHashCode)
            : snapshot.getHash();
    }
}
