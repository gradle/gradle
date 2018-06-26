/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.logical.NormalizedPathFileCollectionSnapshot;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.ZipEntry;

import static org.gradle.api.internal.changedetection.state.InputPathNormalizationStrategy.RELATIVE;

/**
 * Builds the snapshot of a classpath entry.
 * It can be either used on {@link RegularFileSnapshot}s or {@link ZipEntry}.
 * The {@link NormalizedFileSnapshot}s can be collected by a {@link CollectingFileCollectionSnapshotBuilder}.
 */
@SuppressWarnings("Since15")
public class ClasspathEntrySnapshotBuilder implements ResourceWithContentsVisitor {
    private final StringInterner stringInterner;
    private final Multimap<String, NormalizedFileSnapshot> normalizedSnapshots;
    private final ResourceHasher classpathResourceHasher;

    public ClasspathEntrySnapshotBuilder(ResourceHasher classpathResourceHasher, StringInterner stringInterner) {
        this.classpathResourceHasher = classpathResourceHasher;
        this.stringInterner = stringInterner;
        this.normalizedSnapshots = MultimapBuilder.hashKeys().arrayListValues().build();
    }

    @Override
    public void visitFile(Path path, Iterable<String> relativePath, FileContentSnapshot content) {
        HashCode hash = classpathResourceHasher.hash(path, relativePath, content);
        if (hash != null) {
            normalizedSnapshots.put(path.toString(), RELATIVE.getNormalizedSnapshot(path, relativePath, new FileHashSnapshot(hash), stringInterner));
        }
    }

    @Override
    public void visitZipFileEntry(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        HashCode hash = classpathResourceHasher.hash(zipEntry, zipInput);
        if (hash != null) {
            normalizedSnapshots.put(zipEntry.getName(), new DefaultNormalizedFileSnapshot(zipEntry.getName(), new FileHashSnapshot(hash)));
        }
    }

    /**
     * Returns the combined hash of the ClasspathEntry.
     */
    public HashCode getHash() {
        if (normalizedSnapshots.isEmpty()) {
            return null;
        }
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        Collection<NormalizedFileSnapshot> values = normalizedSnapshots.values();
        NormalizedPathFileCollectionSnapshot.appendToHasher(hasher, values);
        return hasher.hash();
    }
}
