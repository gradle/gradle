/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.hash;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
import org.gradle.api.internal.changedetection.state.FileTimeStampInspector;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;
import java.util.Locale;

public class DefaultChecksumService implements ChecksumService {
    private final CachingFileHasher md5;
    private final CachingFileHasher sha1;
    private final CachingFileHasher sha256;
    private final CachingFileHasher sha512;

    public DefaultChecksumService(
        StringInterner stringInterner,
        CrossBuildFileHashCache fileStore,
        FileSystem fileSystem,
        FileTimeStampInspector fileTimeStampInspector,
        FileHasherStatistics.Collector statisticsCollector
    ) {
        md5 = createCache(stringInterner, fileStore, fileSystem, fileTimeStampInspector, "md5", Hashing.md5(), statisticsCollector);
        sha1 = createCache(stringInterner, fileStore, fileSystem, fileTimeStampInspector, "sha1", Hashing.sha1(), statisticsCollector);
        sha256 = createCache(stringInterner, fileStore, fileSystem, fileTimeStampInspector, "sha256", Hashing.sha256(), statisticsCollector);
        sha512 = createCache(stringInterner, fileStore, fileSystem, fileTimeStampInspector, "sha512", Hashing.sha512(), statisticsCollector);
    }

    private CachingFileHasher createCache(
        StringInterner stringInterner,
        CrossBuildFileHashCache fileStore,
        FileSystem fileSystem,
        FileTimeStampInspector fileTimeStampInspector,
        String name,
        HashFunction hashFunction,
        FileHasherStatistics.Collector statisticsCollector
    ) {
        return new CachingFileHasher(new ChecksumHasher(hashFunction), fileStore, stringInterner, fileTimeStampInspector, name + "-checksums", fileSystem, 1000, statisticsCollector);
    }

    @Override
    public HashCode md5(File file) {
        return doHash(file, md5);
    }

    @Override
    public HashCode sha1(File file) {
        return doHash(file, sha1);
    }

    @Override
    public HashCode sha256(File file) {
        return doHash(file, sha256);
    }

    @Override
    public HashCode sha512(File file) {
        return doHash(file, sha512);
    }

    @Override
    public HashCode hash(File src, String algorithm) {
        switch (algorithm.toLowerCase(Locale.ROOT)) {
            case "md5":
                return md5(src);
            case "sha1":
            case "sha-1":
                return sha1(src);
            case "sha256":
            case "sha-256":
                return sha256(src);
            case "sha512":
            case "sha-512":
                return sha512(src);
        }
        throw new UnsupportedOperationException("Cannot hash with algorith " + algorithm);
    }

    private HashCode doHash(File file, CachingFileHasher hasher) {
        return hasher.hash(file);
    }

}
