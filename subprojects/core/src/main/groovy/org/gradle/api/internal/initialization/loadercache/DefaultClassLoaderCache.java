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

import com.google.common.base.Objects;
import org.gradle.api.Nullable;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

public class DefaultClassLoaderCache implements ClassLoaderCache {

    public int getSize() {
        return storage.size();
    }

    public static class Key {
        private final ClassLoader parent;
        private final ClassPathSnapshot classPathSnapshot;
        private final FilteringClassLoader.Spec filterSpec;

        private Key(ClassLoader parent, ClassPathSnapshot classPathSnapshot, FilteringClassLoader.Spec filterSpec) {
            this.parent = parent;
            this.classPathSnapshot = classPathSnapshot;
            this.filterSpec = filterSpec;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (!classPathSnapshot.equals(key.classPathSnapshot)) {
                return false;
            }
            if (filterSpec != null ? !filterSpec.equals(key.filterSpec) : key.filterSpec != null) {
                return false;
            }
            return Objects.equal(parent, key.parent);
        }

        public int hashCode() {
            int result = parent == null ? 0 : parent.hashCode();
            result = 31 * result + classPathSnapshot.hashCode();
            result = 31 * result + (filterSpec != null ? filterSpec.hashCode() : 0);
            return result;
        }
    }

    private final Map<Key, ClassLoader> storage;
    private final Map<ClassLoaderId, Key> idCache = new HashMap<ClassLoaderId, Key>(); //needed for correct invalidation of stale classloaders
    final ClassPathSnapshotter snapshotter;
    private final Object lock = new Object();

    public DefaultClassLoaderCache(Map<Key, ClassLoader> storage) {
        this(storage, new FileClassPathSnapshotter());
    }

    public DefaultClassLoaderCache(Map<Key, ClassLoader> storage, ClassPathSnapshotter snapshotter) {
        this.storage = storage;
        this.snapshotter = snapshotter;
    }

    public ClassLoader get(final ClassLoaderId id, final ClassPath classPath, final ClassLoader parent, @Nullable final FilteringClassLoader.Spec filterSpec) {
        ClassPathSnapshot s = snapshotter.snapshot(classPath);
        Key key = new Key(parent, s, filterSpec);

        synchronized (lock) {
            //if the classloader with given id is already cached
            //invalidate it when the key does not match (e.g. when the classpath or parent has changed)
            invalidateStaleEntries(id, key);

            ClassLoader existingLoader = storage.get(key);
            if (existingLoader != null) {
                idCache.put(id, key);
                return existingLoader;
            } else {
                ClassLoader newLoader = (filterSpec == null) ? new URLClassLoader(classPath.getAsURLArray(), parent) : new FilteringClassLoader(get(id, classPath, parent, null), filterSpec);
                storage.put(key, newLoader);
                return newLoader;
            }
        }
    }

    private void invalidateStaleEntries(ClassLoaderId id, Key key) {
        Key existingKey = idCache.get(id);
        if (existingKey == null) {
            //we haven't yet served classloader with this identifier (or it was previously invalidated)
            idCache.put(id, key); //remember the id
        } else if (!existingKey.equals(key)) {
            //we have already served classloader with this id but the key has changed - invalidate this classloader
            idCache.put(id, key); //refresh the id
            storage.remove(existingKey); //invalidate stale entry
        }
    }
}
