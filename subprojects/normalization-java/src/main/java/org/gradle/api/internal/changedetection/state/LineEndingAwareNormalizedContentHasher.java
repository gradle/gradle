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

import org.gradle.internal.fingerprint.hashing.NormalizedContentHasher;
import org.gradle.internal.hash.FileContentType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.LineEndingNormalizingInputStream;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nullable;
import java.io.File;

import static org.gradle.api.internal.changedetection.state.RegularFileSnapshotContext.*;

/**
 * A {@link NormalizedContentHasher} that detects text files and normalizes the line endings
 * in the content.  For files that are not "known" text files, it simply returns the previously
 * calculated snapshot hash.
 */
public class LineEndingAwareNormalizedContentHasher implements NormalizedContentHasher {
    private final NormalizedContentInfoCollector hasher;
    private final ResourceSnapshotterCacheService cacheService;
    private final HashCode configurationHashCode;

    public LineEndingAwareNormalizedContentHasher(StreamHasher streamHasher, ResourceSnapshotterCacheService cacheService) {
        this.hasher = new NormalizedContentInfoCollector(LineEndingNormalizingInputStream::new, streamHasher);
        this.cacheService = cacheService;
        this.configurationHashCode = getConfigurationHashCode();
    }

    private static HashCode getConfigurationHashCode() {
        Hasher hasher = Hashing.newHasher();
        hasher.putString(LineEndingAwareNormalizedContentHasher.class.getName());
        hasher.putString(LineEndingNormalizingInputStream.class.getName());
        return hasher.hash();
    }

    @Nullable
    @Override
    public HashCode hashContent(RegularFileSnapshot snapshot) {
        NormalizedContentInfoCollector.NormalizedContentInfo normalizedContentInfo = hasher.collect(new File(snapshot.getAbsolutePath()));
        return cacheService.hashFile(
            from(snapshot),
            s -> normalizedContentInfo.getContentType() == FileContentType.TEXT ?
                    normalizedContentInfo.getHash() :
                    snapshot.getHash(),
            configurationHashCode
        );
    }


}
