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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.util.Map;
import java.util.Set;

public class ConfigurableClassLoaderHierarchyHasher implements ClassLoaderHierarchyHasher {
    private static final byte[] UNKNOWN = "unknown".getBytes(Charsets.UTF_8);
    private final Map<ClassLoader, byte[]> knownClassLoaders;

    protected ConfigurableClassLoaderHierarchyHasher(Map<ClassLoader, String> knownClassLoaders) {
        ImmutableMap.Builder<ClassLoader, byte[]> hashesBuilder = ImmutableMap.builder();
        for (Map.Entry<ClassLoader, String> entry : knownClassLoaders.entrySet()) {
            hashesBuilder.put(entry.getKey(), entry.getValue().getBytes(Charsets.UTF_8));
        }
        this.knownClassLoaders = hashesBuilder.build();
    }

    @Override
    public HashCode getHash(ClassLoader classLoader) {
        Visitor visitor = new Visitor();
        visitor.visit(classLoader);
        return visitor.getHash();
    }

    private class Visitor extends ClassLoaderVisitor {
        private final Hasher hasher = Hashing.md5().newHasher();
        private final Set<ClassLoader> alreadyHashedLoaders = Sets.newHashSet();

        @Override
        public void visit(ClassLoader classLoader) {
            ClassLoader end = ClassLoader.getSystemClassLoader();
            if (classLoader != null && classLoader != end) {
                addToHash(classLoader);
            }
            super.visit(classLoader);
        }

        public HashCode getHash() {
            return hasher.hash();
        }

        private boolean addToHash(ClassLoader cl) {
            if (alreadyHashedLoaders.contains(cl)) {
                return true;
            }
            byte[] knownId = knownClassLoaders.get(cl);
            if (knownId != null) {
                hasher.putBytes(knownId);
                return true;
            }
            if (cl instanceof CachingClassLoader || cl instanceof MultiParentClassLoader) {
                return true;
            }
            if (cl instanceof HashedClassLoader) {
                hasher.putBytes(((HashedClassLoader) cl).getClassLoaderHash().asBytes());
                alreadyHashedLoaders.add(cl.getParent());
                return true;
            }
            hasher.putBytes(UNKNOWN);
            return false;
        }
    }
}
