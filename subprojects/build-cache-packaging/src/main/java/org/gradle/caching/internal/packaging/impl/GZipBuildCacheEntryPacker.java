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

package org.gradle.caching.internal.packaging.impl;

import org.gradle.caching.internal.origin.OriginReader;
import org.gradle.caching.internal.origin.OriginWriter;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.CacheableTree;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.SortedSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZipBuildCacheEntryPacker implements BuildCacheEntryPacker {
    private final BuildCacheEntryPacker delegate;

    public GZipBuildCacheEntryPacker(BuildCacheEntryPacker delegate) {
        this.delegate = delegate;
    }

    @Override
    public PackResult pack(SortedSet<? extends CacheableTree> trees, Map<String, CurrentFileCollectionFingerprint> fingerprints, OutputStream output, OriginWriter writeOrigin) throws IOException {
        try (GZIPOutputStream gzipOutput = new GZIPOutputStream(output)) {
            return delegate.pack(trees, fingerprints, gzipOutput, writeOrigin);
        }
    }

    @Override
    public UnpackResult unpack(SortedSet<? extends CacheableTree> trees, InputStream input, OriginReader readOrigin) throws IOException {
        try (GZIPInputStream gzipInput = new GZIPInputStream(input)) {
            return delegate.unpack(trees, gzipInput, readOrigin);
        }
    }
}
