/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.Transformer;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
@ServiceScope(Scope.Global.class)
public class ClassLoaderCache {
    private final Lock lock = new ReentrantLock();
    private final Cache<ClassLoader, ClassLoaderDetails> classLoaderDetails;
    private final Cache<UUID, ClassLoader> classLoaderIds;

    public ClassLoaderCache() {
        classLoaderDetails = CacheBuilder.newBuilder().weakKeys().build();
        classLoaderIds = CacheBuilder.newBuilder().softValues().build();
    }

    public ClassLoader getClassLoader(ClassLoaderDetails details, Transformer<ClassLoader, ClassLoaderDetails> factory) {
        lock.lock();
        try {
            ClassLoader classLoader = classLoaderIds.getIfPresent(details.uuid);
            if (classLoader != null) {
                return classLoader;
            }

            classLoader = factory.transform(details);
            classLoaderIds.put(details.uuid, classLoader);
            classLoaderDetails.put(classLoader, details);
            return classLoader;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public ClassLoaderDetails maybeGetDetails(ClassLoader classLoader) {
        lock.lock();
        try {
            return classLoaderDetails.getIfPresent(classLoader);
        } finally {
            lock.unlock();
        }
    }

    public ClassLoaderDetails getDetails(ClassLoader classLoader, Transformer<ClassLoaderDetails, ClassLoader> factory) {
        lock.lock();
        try {
            ClassLoaderDetails details = classLoaderDetails.getIfPresent(classLoader);
            if (details != null) {
                return details;
            }

            details = factory.transform(classLoader);
            classLoaderDetails.put(classLoader, details);
            classLoaderIds.put(details.uuid, classLoader);
            return details;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all entries in the cache.
     */
    public void clear() {
        lock.lock();
        try {
            for (ClassLoader classLoader : classLoaderDetails.asMap().keySet()) {
                ClassLoaderUtils.tryClose(classLoader);
            }
            classLoaderDetails.invalidateAll();
            classLoaderIds.invalidateAll();
        } finally {
            lock.unlock();
        }
    }
}
