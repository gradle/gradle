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

package org.gradle.internal.classloader;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public class ConfigurableClassLoaderHierarchyHasher implements ClassLoaderHierarchyHasher {
    private final Map<ClassLoader, byte[]> knownClassLoaders;
    private final ClassLoaderHasher classLoaderHasher;

    public ConfigurableClassLoaderHierarchyHasher(Map<ClassLoader, String> knownClassLoaders, ClassLoaderHasher classLoaderHasher) {
        this.classLoaderHasher = classLoaderHasher;
        Map<ClassLoader, byte[]> hashes = new WeakHashMap<ClassLoader, byte[]>();
        for (Map.Entry<ClassLoader, String> entry : knownClassLoaders.entrySet()) {
            hashes.put(entry.getKey(), entry.getValue().getBytes(Charsets.UTF_8));
        }
        this.knownClassLoaders = hashes;
    }

    @Nullable
    @Override
    public HashCode getClassLoaderHash(ClassLoader classLoader) {
        Visitor visitor = new Visitor();
        visitor.visit(classLoader);
        return visitor.getHash();
    }

    private class Visitor extends ClassLoaderVisitor {
        private final Hasher hasher = Hashing.md5().newHasher();
        private boolean foundUnknown;

        @Override
        public void visit(ClassLoader classLoader) {
            if (addToHash(classLoader)) {
                super.visit(classLoader);
            }
        }

        public HashCode getHash() {
            return foundUnknown ? null : hasher.hash();
        }

        private boolean addToHash(ClassLoader cl) {
            byte[] knownId = knownClassLoaders.get(cl);
            if (knownId != null) {
                hasher.putBytes(knownId);
                return false;
            }
            if (cl instanceof CachingClassLoader || cl instanceof MultiParentClassLoader) {
                return true;
            }
            HashCode hash = classLoaderHasher.getHash(cl);
            if (hash != null) {
                hasher.putBytes(hash.asBytes());
                return true;
            }
            foundUnknown = true;
            return false;
        }
    }
}
