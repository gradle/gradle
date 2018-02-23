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

import com.google.common.base.Function;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

import static org.gradle.api.internal.changedetection.state.InputPathNormalizationStrategy.RELATIVE;

/**
 * Builds the snapshot of a classpath entry.
 * It can be either used on {@link RegularFileSnapshot}s or {@link ZipEntry}.
 * The {@link NormalizedFileSnapshot}s can be collected by a {@link CollectingFileCollectionSnapshotBuilder}.
 */
public class ClasspathEntrySnapshotBuilder implements ResourceWithContentsVisitor {
    private static final Ordering<Map.Entry<String, NormalizedFileSnapshot>> SNAPSHOT_ENTRY_ORDERING = Ordering.natural().onResultOf(new Function<Map.Entry<String, NormalizedFileSnapshot>, Comparable<NormalizedFileSnapshot>>() {
        @Override
        public NormalizedFileSnapshot apply(Map.Entry<String, NormalizedFileSnapshot> input) {
            return input.getValue();
        }
    });
    private final StringInterner stringInterner;
    private final Multimap<String, NormalizedFileSnapshot> normalizedSnapshots;
    private final ResourceHasher classpathResourceHasher;

    public ClasspathEntrySnapshotBuilder(ResourceHasher classpathResourceHasher, StringInterner stringInterner) {
        this.classpathResourceHasher = classpathResourceHasher;
        this.stringInterner = stringInterner;
        this.normalizedSnapshots = MultimapBuilder.hashKeys().arrayListValues().build();
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        HashCode hash = classpathResourceHasher.hash(file);
        if (hash != null) {
            normalizedSnapshots.put(file.getPath(), RELATIVE.getNormalizedSnapshot(file.withContentHash(hash), stringInterner));
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
        TaskFilePropertyCompareStrategy.UNORDERED.appendToHasher(hasher, values);
        return hasher.hash();
    }

    public void collectNormalizedSnapshots(CollectingFileCollectionSnapshotBuilder builder) {
        if (normalizedSnapshots.isEmpty()) {
            return;
        }
        List<Map.Entry<String, NormalizedFileSnapshot>> sorted = new ArrayList<Map.Entry<String, NormalizedFileSnapshot>>(normalizedSnapshots.entries());
        Collections.sort(sorted, SNAPSHOT_ENTRY_ORDERING);
        for (Map.Entry<String, NormalizedFileSnapshot> normalizedFileSnapshotEntry : sorted) {
            builder.collectNormalizedFileSnapshot(normalizedFileSnapshotEntry.getKey(), normalizedFileSnapshotEntry.getValue());
        }
    }
}
