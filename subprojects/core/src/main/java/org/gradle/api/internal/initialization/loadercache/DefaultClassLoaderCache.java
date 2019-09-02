/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.initialization.SessionLifecycleListener;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.hash.HashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Set;

public class DefaultClassLoaderCache implements ClassLoaderCache, Stoppable, SessionLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClassLoaderCache.class);

    private final Object lock = new Object();
    private final Map<ClassLoaderSpec, ClassLoader> bySpec = Maps.newHashMap();
    private final Map<ClassLoaderSpec, SoftReference<ClassLoader>> previousBySpec = Maps.newHashMap();
    private final Set<ClassLoaderSpec> usedInThisBuild = Sets.newHashSet();
    private final ClasspathHasher classpathHasher;
    private final HashingClassLoaderFactory classLoaderFactory;

    public DefaultClassLoaderCache(HashingClassLoaderFactory classLoaderFactory, ClasspathHasher classpathHasher) {
        this.classLoaderFactory = classLoaderFactory;
        this.classpathHasher = classpathHasher;
    }

    @Override
    public ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec) {
        return get(id, classPath, parent, filterSpec, null);
    }

    @Override
    public ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec, HashCode implementationHash) {
        if (implementationHash == null) {
            implementationHash = classpathHasher.hash(classPath);
        }
        ManagedClassLoaderSpec spec = new ManagedClassLoaderSpec(id.toString(), parent, classPath, implementationHash, filterSpec);

        synchronized (lock) {
            return getAndRetainLoader(classPath, spec);
        }
    }

    @Override
    public <T extends ClassLoader> T put(ClassLoaderId id, T classLoader) {
        synchronized (lock) {
            ClassLoaderSpec spec = new UnmanagedClassLoaderSpec(id);
            bySpec.put(spec, classLoader);
            usedInThisBuild.add(spec);
        }
        return classLoader;
    }

    private ClassLoader getAndRetainLoader(ClassPath classPath, ManagedClassLoaderSpec spec) {
        ClassLoader classLoader = bySpec.get(spec);
        if (classLoader == null) {
            SoftReference<ClassLoader> reference = previousBySpec.remove(spec);
            if (reference != null) {
                classLoader = reference.get();
            }
            if (classLoader == null) {
                if (spec.isFiltered()) {
                    ClassLoader parentCachedLoader = getAndRetainLoader(classPath, spec.unfiltered());
                    classLoader = classLoaderFactory.createFilteringClassLoader(parentCachedLoader, spec.filterSpec);
                } else {
                    classLoader = classLoaderFactory.createChildClassLoader(spec.name, spec.parent, classPath, spec.implementationHash);
                }
            }
            bySpec.put(spec, classLoader);
        }
        usedInThisBuild.add(spec);
        return classLoader;
    }

    @VisibleForTesting
    public int size() {
        synchronized (lock) {
            return bySpec.size();
        }
    }

    @VisibleForTesting
    public int retained() {
        synchronized (lock) {
            return previousBySpec.size();
        }
    }

    @VisibleForTesting
    public void releaseReferences() {
        synchronized (lock) {
            for (SoftReference<ClassLoader> value : previousBySpec.values()) {
                value.clear();
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            for (Map.Entry<ClassLoaderSpec, ClassLoader> entry : bySpec.entrySet()) {
                discard(entry.getKey(), entry.getValue());
            }
            bySpec.clear();
            usedInThisBuild.clear();
        }
    }

    private void discard(ClassLoaderSpec spec, ClassLoader classLoader) {
        ClassLoaderUtils.tryClose(classLoader);
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        synchronized (lock) {
            Set<ClassLoaderSpec> unused = Sets.newHashSet(bySpec.keySet());
            unused.removeAll(usedInThisBuild);
            for (ClassLoaderSpec spec : unused) {
                ClassLoader classLoader = bySpec.remove(spec);
                previousBySpec.put(spec, new SoftReference<>(classLoader));
            }
            usedInThisBuild.clear();
            previousBySpec.values().removeIf(entry -> entry.get() == null);
        }
    }

    private static abstract class ClassLoaderSpec {
    }

    private static class UnmanagedClassLoaderSpec extends ClassLoaderSpec {
        private final ClassLoaderId id;

        public UnmanagedClassLoaderSpec(ClassLoaderId id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            UnmanagedClassLoaderSpec other = (UnmanagedClassLoaderSpec) obj;
            return other.id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static class ManagedClassLoaderSpec extends ClassLoaderSpec {
        private final String name;
        private final ClassLoader parent;
        private final ClassPath classPath;
        private final HashCode implementationHash;
        private final FilteringClassLoader.Spec filterSpec;

        public ManagedClassLoaderSpec(String name, ClassLoader parent, ClassPath classPath, HashCode implementationHash, FilteringClassLoader.Spec filterSpec) {
            this.name = name;
            this.parent = parent;
            this.classPath = classPath;
            this.implementationHash = implementationHash;
            this.filterSpec = filterSpec;
        }

        public ManagedClassLoaderSpec unfiltered() {
            return new ManagedClassLoaderSpec(name, parent, classPath, implementationHash, null);
        }

        @Override
        public String toString() {
            return name + "," + System.identityHashCode(parent) + "," + (filterSpec == null ? "-" : "filtered");
        }

        public boolean isFiltered() {
            return filterSpec != null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            ManagedClassLoaderSpec that = (ManagedClassLoaderSpec) o;
            return Objects.equal(this.parent, that.parent)
                && this.implementationHash.equals(that.implementationHash)
                && this.classPath.equals(that.classPath)
                && Objects.equal(this.filterSpec, that.filterSpec);
        }

        @Override
        public int hashCode() {
            int result = implementationHash.hashCode();
            result = 31 * result + classPath.hashCode();
            result = 31 * result + (filterSpec != null ? filterSpec.hashCode() : 0);
            result = 31 * result + (parent != null ? parent.hashCode() : 0);
            return result;
        }
    }
}
