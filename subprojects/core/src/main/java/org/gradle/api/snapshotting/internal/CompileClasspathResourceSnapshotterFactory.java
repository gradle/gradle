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

package org.gradle.api.snapshotting.internal;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.CachingResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ClasspathResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.state.CompileClasspathEntryResourceSnapshotter;
import org.gradle.api.snapshotting.CompileClasspath;
import org.gradle.cache.PersistentIndexedCache;

public class CompileClasspathResourceSnapshotterFactory implements ResourceSnapshotterFactory<CompileClasspath> {
    private final StringInterner stringInterner;
    private final PersistentIndexedCache<HashCode, HashCode> cache;

    public CompileClasspathResourceSnapshotterFactory(StringInterner stringInterner, PersistentIndexedCache<HashCode, HashCode> cache) {
        this.stringInterner = stringInterner;
        this.cache = cache;
    }

    @Override
    public ResourceSnapshotter create(CompileClasspath configuration) {
        ResourceSnapshotter classpathEntrySnapshotter = new CompileClasspathEntryResourceSnapshotter(cache, stringInterner);
        return new CachingResourceSnapshotter(
            new ClasspathResourceSnapshotter(classpathEntrySnapshotter, stringInterner),
            cache
        );
    }
}
