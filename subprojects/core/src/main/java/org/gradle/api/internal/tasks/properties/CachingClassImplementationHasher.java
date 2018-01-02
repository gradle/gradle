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

package org.gradle.api.internal.tasks.properties;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.api.internal.changedetection.state.ImplementationSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

public class CachingClassImplementationHasher implements ClassImplementationHasher {
    private static final HashCode NULL_HASH = HashCode.fromBytes(new byte[8]);

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final LoadingCache<Class<?>, HashCode> classImplementationHashes = CacheBuilder.newBuilder().build(new CacheLoader<Class<?>, HashCode>() {
        @Override
        public HashCode load(Class<?> key) {
            HashCode hash = hash(key);
            return hash == null ? NULL_HASH : hash;
        }
    });

    public CachingClassImplementationHasher(ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
    }

    @Override
    public HashCode getImplementationHash(Class<?> type) {
        HashCode hash = classImplementationHashes.getUnchecked(type);
        return hash == NULL_HASH ? null : hash;
    }

    @Nullable
    private HashCode hash(Class<?> type) {
        BuildCacheHasher buildCacheHasher = new DefaultBuildCacheHasher();
        HashCode classLoaderHash = classLoaderHierarchyHasher.getClassLoaderHash(type.getClassLoader());
        if (classLoaderHash == null) {
            return null;
        }
        String typeName = type.getName();
        new ImplementationSnapshot(typeName, classLoaderHash).appendToHasher(buildCacheHasher);
        return buildCacheHasher.hash();
    }
}
