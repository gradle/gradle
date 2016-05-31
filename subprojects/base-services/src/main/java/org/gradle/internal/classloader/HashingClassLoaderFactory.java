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
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.internal.classpath.ClassPath;

import java.util.Collections;
import java.util.List;

public class HashingClassLoaderFactory extends DefaultClassLoaderFactory {
    private final ClassPathSnapshotter snapshotter;

    public HashingClassLoaderFactory(ClassPathSnapshotter snapshotter) {
        this.snapshotter = snapshotter;
    }

    protected ClassLoader doCreateClassLoader(ClassLoader parent, ClassPath classpath) {
        ClassLoader classLoader = super.doCreateClassLoader(parent, classpath);
        HashCode hashCode = snapshotter.snapshot(classpath).getStrongHash();
        return new HashedClassLoader(classLoader, hashCode);
    }

    protected ClassLoader doCreateFilteringClassLoader(ClassLoader parent, FilteringClassLoader.Spec spec) {
        ClassLoader classLoader = super.doCreateFilteringClassLoader(parent, spec);
        HashCode filterHash = calculateFilterSpecHash(spec);
        return new HashedClassLoader(classLoader, filterHash);
    }

    private static HashCode calculateFilterSpecHash(FilteringClassLoader.Spec spec) {
        Hasher hasher = Hashing.md5().newHasher();
        addToHash(hasher, spec.getClassNames());
        addToHash(hasher, spec.getPackageNames());
        addToHash(hasher, spec.getPackagePrefixes());
        addToHash(hasher, spec.getResourcePrefixes());
        addToHash(hasher, spec.getResourceNames());
        addToHash(hasher, spec.getDisallowedClassNames());
        addToHash(hasher, spec.getDisallowedPackagePrefixes());
        return hasher.hash();
    }

    private static void addToHash(Hasher hasher, Iterable<String> items) {
        List<String> sortedItems = Lists.newArrayList(items);
        Collections.sort(sortedItems);
        for (String item : sortedItems) {
            hasher.putInt(0);
            hasher.putString(item, Charsets.UTF_8);
        }
    }
}
