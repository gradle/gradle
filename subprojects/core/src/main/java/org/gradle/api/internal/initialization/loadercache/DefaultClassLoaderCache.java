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

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.hash.HashCode;
import org.gradle.api.Nullable;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classloader.ClassPathSnapshot;
import org.gradle.internal.classloader.ClassPathSnapshotter;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.Stoppable;

import java.util.Map;

public class DefaultClassLoaderCache implements ClassLoaderCache, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultClassLoaderCache.class);

    private final Object lock = new Object();
    private final Map<ClassLoaderId, CachedClassLoader> byId = Maps.newHashMap();
    private final Map<ClassLoaderSpec, CachedClassLoader> bySpec = Maps.newHashMap();
    private final ClassPathSnapshotter snapshotter;
    private final HashingClassLoaderFactory classLoaderFactory;

    public DefaultClassLoaderCache(HashingClassLoaderFactory classLoaderFactory, ClassPathSnapshotter snapshotter) {
        this.classLoaderFactory = classLoaderFactory;
        this.snapshotter = snapshotter;
    }

    @Override
    public ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec) {
        return get(id, classPath, parent, filterSpec, null);
    }

    @Override
    public ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec, HashCode overrideHashCode) {
        ClassPathSnapshot classPathSnapshot = snapshotter.snapshot(classPath);
        ClassLoaderSpec spec = new ClassLoaderSpec(parent, classPathSnapshot, filterSpec, overrideHashCode);

        synchronized (lock) {
            CachedClassLoader cachedLoader = byId.get(id);
            if (cachedLoader == null || !cachedLoader.is(spec)) {
                CachedClassLoader newLoader = getAndRetainLoader(classPath, spec, id);
                byId.put(id, newLoader);

                if (cachedLoader != null) {
                    LOGGER.debug("Releasing previous classloader for {}", id);
                    cachedLoader.release(id);
                }

                return newLoader.classLoader;
            } else {
                return cachedLoader.classLoader;
            }
        }
    }

    @Override
    public void remove(ClassLoaderId id) {
        synchronized (lock) {
            CachedClassLoader cachedClassLoader = byId.remove(id);
            if (cachedClassLoader != null) {
                cachedClassLoader.release(id);
            }
        }
    }

    private CachedClassLoader getAndRetainLoader(ClassPath classPath, ClassLoaderSpec spec, ClassLoaderId id) {
        CachedClassLoader cachedLoader = bySpec.get(spec);
        if (cachedLoader == null) {
            ClassLoader classLoader;
            CachedClassLoader parentCachedLoader = null;
            if (spec.isFiltered()) {
                parentCachedLoader = getAndRetainLoader(classPath, spec.unfiltered(), id);
                classLoader = classLoaderFactory.createFilteringClassLoader(parentCachedLoader.classLoader, spec.filterSpec);
            } else {
                classLoader = classLoaderFactory.createChildClassLoader(spec.parent, classPath, spec.overrideHashCode);
            }
            cachedLoader = new CachedClassLoader(classLoader, spec, parentCachedLoader);
            bySpec.put(spec, cachedLoader);
        }

        return cachedLoader.retain(id);
    }

    @Override
    public int size() {
        synchronized (lock) {
            return bySpec.size();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            for (CachedClassLoader cachedClassLoader : byId.values()) {
                ClassLoaderUtils.tryClose(cachedClassLoader.classLoader);
            }
            byId.clear();
            bySpec.clear();
        }
    }

    private static class ClassLoaderSpec {
        private final ClassLoader parent;
        private final ClassPathSnapshot classPathSnapshot;
        private final FilteringClassLoader.Spec filterSpec;
        private final HashCode overrideHashCode;

        public ClassLoaderSpec(ClassLoader parent, ClassPathSnapshot classPathSnapshot, FilteringClassLoader.Spec filterSpec, HashCode overrideHashCode) {
            this.parent = parent;
            this.classPathSnapshot = classPathSnapshot;
            this.filterSpec = filterSpec;
            this.overrideHashCode = overrideHashCode;
        }

        public ClassLoaderSpec unfiltered() {
            return new ClassLoaderSpec(parent, classPathSnapshot, null, overrideHashCode);
        }

        public boolean isFiltered() {
            return filterSpec != null;
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object o) {
            ClassLoaderSpec that = (ClassLoaderSpec) o;
            return Objects.equal(this.parent, that.parent)
                && this.classPathSnapshot.equals(that.classPathSnapshot)
                && Objects.equal(this.filterSpec, that.filterSpec)
                && Objects.equal(this.overrideHashCode, that.overrideHashCode);
        }

        @Override
        public int hashCode() {
            int result = classPathSnapshot.hashCode();
            result = 31 * result + (filterSpec != null ? filterSpec.hashCode() : 0);
            result = 31 * result + (parent != null ? parent.hashCode() : 0);
            result = 31 * result + (overrideHashCode != null ? overrideHashCode.hashCode() : 0);
            return result;
        }
    }

    private class CachedClassLoader {
        private final ClassLoader classLoader;
        private final ClassLoaderSpec spec;
        private final CachedClassLoader parent;
        private final Multiset<ClassLoaderId> usedBy = HashMultiset.create();

        private CachedClassLoader(ClassLoader classLoader, ClassLoaderSpec spec, @Nullable CachedClassLoader parent) {
            this.classLoader = classLoader;
            this.spec = spec;
            this.parent = parent;
        }

        public boolean is(ClassLoaderSpec spec) {
            return this.spec.equals(spec);
        }

        public CachedClassLoader retain(ClassLoaderId loaderId) {
            usedBy.add(loaderId);
            return this;
        }

        public void release(ClassLoaderId loaderId) {
            if (usedBy.isEmpty()) {
                throw new IllegalStateException("Cannot release already released classloader: " + classLoader);
            }

            if (usedBy.remove(loaderId)) {
                if (usedBy.isEmpty()) {
                    if (parent != null) {
                        parent.release(loaderId);
                    }
                    bySpec.remove(spec);
                }
            } else {
                throw new IllegalStateException("Classloader '" + this + "' not used by '" + loaderId + "'");
            }
        }
    }

    // Used in org.gradle.api.internal.initialization.loadercache.ClassLoadersCachingIntegrationTest
    @SuppressWarnings("UnusedDeclaration")
    public void assertInternalIntegrity() {
        synchronized (lock) {
            Map<ClassLoaderId, CachedClassLoader> orphaned = Maps.newHashMap();
            for (Map.Entry<ClassLoaderId, CachedClassLoader> entry : byId.entrySet()) {
                if (!bySpec.containsKey(entry.getValue().spec)) {
                    orphaned.put(entry.getKey(), entry.getValue());
                }
            }

            if (!orphaned.isEmpty()) {
                throw new IllegalStateException("The following class loaders are orphaned: " + Joiner.on(",").withKeyValueSeparator(":").join(orphaned));
            }
        }
    }
}
