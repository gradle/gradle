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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.cache.Cache;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.hash.HashCode;

public class DefaultGeneralCompileCaches implements GeneralCompileCaches {

    private final Cache<HashCode, ClasspathEntrySnapshotData> snapshotCache;
    private final Cache<HashCode, ClassAnalysis> classAnalysisCache;

    public DefaultGeneralCompileCaches(UserHomeScopedCompileCaches userHomeScopedCompileCaches, BuildCacheController buildCache, StringInterner interner) {
        snapshotCache = new BuildCacheClasspathEntrySnapshotCache(
            userHomeScopedCompileCaches.getClasspathEntrySnapshotCache(),
            buildCache,
            new ClasspathEntrySnapshotData.Serializer(interner)
        );
        classAnalysisCache = userHomeScopedCompileCaches.getClassAnalysisCache();
    }

    @Override
    public Cache<HashCode, ClasspathEntrySnapshotData> getClasspathEntrySnapshotCache() {
        return snapshotCache;
    }

    @Override
    public Cache<HashCode, ClassAnalysis> getClassAnalysisCache() {
        return classAnalysisCache;
    }
}
