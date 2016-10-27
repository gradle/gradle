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

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.classloader.ClassPathSnapshotter;
import org.gradle.internal.classpath.ClassPath;

import java.io.File;

import static org.gradle.internal.hash.HashUtil.createCompactMD5;

class DefaultCacheKeyBuilder implements CacheKeyBuilder {

    private final HashFunction hashFunction;
    private final FileHasher fileHasher;
    private final ClassPathSnapshotter snapshotter;

    public DefaultCacheKeyBuilder(HashFunction hashFunction, FileHasher fileHasher, ClassPathSnapshotter snapshotter) {
        this.hashFunction = hashFunction;
        this.fileHasher = fileHasher;
        this.snapshotter = snapshotter;
    }

    @Override
    public String build(CacheKeySpec spec) {
        String prefix = spec.getPrefix();
        Object[] components = spec.getComponents();
        switch (components.length) {
            case 0:
                return prefix;
            case 1:
                return prefix + "/" + createCompactMD5(hashOf(components[0]));
            default:
                return prefix + "/" + createCompactMD5(combinedHashOf(components));
        }
    }

    private HashCode hashOf(Object component) {
        if (component instanceof String) {
            return hashFunction.hashString((String) component, Charsets.UTF_8);
        }
        if (component instanceof File) {
            return fileHasher.hash((File) component);
        }
        if (component instanceof ClassPath) {
            return snapshotter.snapshot((ClassPath) component).getStrongHash();
        }
        throw new IllegalStateException("Unsupported cache key component type: " + component.getClass().getName());
    }

    private HashCode combinedHashOf(Object[] components) {
        Hasher hasher = hashFunction.newHasher();
        for (Object c : components) {
            if (c instanceof String) {
                hasher.putString((String) c, Charsets.UTF_8);
            } else {
                // TODO: optimize away the intermediate ClassPath hashes by introducing `ClasspathHasher#hashInto(Hasher, ClassPath)`
                hasher.putBytes(hashOf(c).asBytes());
            }
        }
        return hasher.hash();
    }
}
