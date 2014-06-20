/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.cache.CacheRepository;

public class DefaultCompilationCaches implements CompilationCaches {

    private final ClassAnalysisCache classAnalysisCache;
    private final JarSnapshotCache jarSnapshotCache;
    private final CacheRepository cacheRepository;

    public DefaultCompilationCaches(CacheRepository cacheRepository, ClassAnalysisCache classAnalysisCache, JarSnapshotCache jarSnapshotCache) {
        this.cacheRepository = cacheRepository;
        this.classAnalysisCache = classAnalysisCache;
        this.jarSnapshotCache = jarSnapshotCache;
    }

    public ClassAnalysisCache getClassAnalysisCache() {
        return classAnalysisCache;
    }

    public JarSnapshotCache getJarSnapshotCache() {
        return jarSnapshotCache;
    }

    public LocalJarHashesStore getLocalJarHashesStore(JavaCompile javaCompile) {
        return new LocalJarHashesStore(cacheRepository, javaCompile);
    }

    public LocalClassDependencyInfoStore getLocalClassDependencyInfoStore(JavaCompile javaCompile) {
        return new LocalClassDependencyInfoStore(cacheRepository, javaCompile);
    }
}