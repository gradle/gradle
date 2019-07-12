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

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class DefaultHashingClassLoaderFactory extends DefaultClassLoaderFactory implements HashingClassLoaderFactory {
    private final ClasspathHasher classpathHasher;
    private final Map<ClassLoader, HashCode> hashCodes = Collections.synchronizedMap(new WeakHashMap<ClassLoader, HashCode>());

    public DefaultHashingClassLoaderFactory(ClasspathHasher classpathHasher) {
        this.classpathHasher = classpathHasher;
    }

    @Override
    protected ClassLoader doCreateClassLoader(String name, ClassLoader parent, ClassPath classPath) {
        ClassLoader classLoader = super.doCreateClassLoader(name, parent, classPath);
        hashCodes.put(classLoader, calculateClassLoaderHash(classPath));
        return classLoader;
    }

    @Override
    protected ClassLoader doCreateFilteringClassLoader(ClassLoader parent, FilteringClassLoader.Spec spec) {
        ClassLoader classLoader = super.doCreateFilteringClassLoader(parent, spec);
        hashCodes.put(classLoader, calculateFilterSpecHash(spec));
        return classLoader;
    }

    @Override
    public ClassLoader createChildClassLoader(String name, ClassLoader parent, ClassPath classPath, HashCode implementationHash) {
        HashCode hashCode = implementationHash != null
            ? implementationHash
            : calculateClassLoaderHash(classPath);
        ClassLoader classLoader = super.doCreateClassLoader(name, parent, classPath);
        hashCodes.put(classLoader, hashCode);
        return classLoader;
    }

    @Override
    public HashCode getClassLoaderClasspathHash(ClassLoader classLoader) {
        if (classLoader instanceof ImplementationHashAware) {
            ImplementationHashAware loader = (ImplementationHashAware) classLoader;
            return loader.getImplementationHash();
        }
        return hashCodes.get(classLoader);
    }

    private HashCode calculateClassLoaderHash(ClassPath classPath) {
        return classpathHasher.hash(classPath);
    }

    private static HashCode calculateFilterSpecHash(FilteringClassLoader.Spec spec) {
        Hasher hasher = Hashing.newHasher();
        addToHash(hasher, spec.getClassNames());
        addToHash(hasher, spec.getPackageNames());
        addToHash(hasher, spec.getPackagePrefixes());
        addToHash(hasher, spec.getResourcePrefixes());
        addToHash(hasher, spec.getResourceNames());
        addToHash(hasher, spec.getDisallowedClassNames());
        addToHash(hasher, spec.getDisallowedPackagePrefixes());
        return hasher.hash();
    }

    private static void addToHash(Hasher hasher, Set<String> items) {
        int count = items.size();
        hasher.putInt(count);
        if (count == 0) {
            return;
        }
        String[] sortedItems = items.toArray(new String[count]);
        Arrays.sort(sortedItems);
        for (String item : sortedItems) {
            hasher.putString(item);
        }
    }
}
