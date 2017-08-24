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

package org.gradle.cache.internal;

import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.HashFunction;
import org.gradle.internal.hash.Hasher;

import java.io.File;

import static org.gradle.internal.hash.HashUtil.compactStringFor;

class DefaultCacheKeyBuilder implements CacheKeyBuilder {

    private final HashFunction hashFunction;
    private final FileHasher fileHasher;
    private final ClasspathHasher classpathHasher;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;

    public DefaultCacheKeyBuilder(HashFunction hashFunction,
                                  FileHasher fileHasher,
                                  ClasspathHasher classpathHasher,
                                  ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        this.hashFunction = hashFunction;
        this.fileHasher = fileHasher;
        this.classpathHasher = classpathHasher;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
    }

    @Override
    public String build(CacheKeySpec spec) {
        String prefix = spec.getPrefix();
        Object[] components = spec.getComponents();
        switch (components.length) {
            case 0:
                return prefix;
            case 1:
                return prefix + "/" + compactStringFor(hashOf(components[0]));
            default:
                return prefix + "/" + compactStringFor(combinedHashOf(components));
        }
    }

    private HashCode hashOf(Object component) {
        if (component instanceof String) {
            return hashFunction.hashString((String) component);
        }
        if (component instanceof File) {
            return fileHasher.hash((File) component);
        }
        if (component instanceof ClassLoader) {
            return strictHashOf((ClassLoader) component);
        }
        if (component instanceof ClassPath) {
            return classpathHasher.hash((ClassPath) component);
        }
        throw new IllegalStateException("Unsupported cache key component type: " + component.getClass().getName());
    }

    private HashCode strictHashOf(ClassLoader classLoader) {
        HashCode strictHash = classLoaderHierarchyHasher.getClassLoaderHash(classLoader);
        if (strictHash == null) {
            throw new IllegalArgumentException("Unknown classloader: " + classLoader);
        }
        return strictHash;
    }

    private HashCode combinedHashOf(Object[] components) {
        Hasher hasher = hashFunction.newHasher();
        for (Object c : components) {
            hasher.putBytes(hashOf(c).toByteArray());
        }
        return hasher.hash();
    }
}
