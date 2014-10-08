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

import com.google.common.cache.Cache;
import org.gradle.api.Nullable;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class DefaultClassLoaderCache implements ClassLoaderCache {

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
            if (!parent.equals(key.parent)) {
                return false;
            }

            return true;
        }

        public int hashCode() {
            int result = parent.hashCode();
            result = 31 * result + classPathSnapshot.hashCode();
            result = 31 * result + (filterSpec != null ? filterSpec.hashCode() : 0);
            return result;
        }
    }

    private final Cache<Key, ClassLoader> cache;
    final ClassPathSnapshotter snapshotter;

    public DefaultClassLoaderCache(Cache<Key, ClassLoader> cache) {
        this(cache, new FileClassPathSnapshotter());
    }

    public DefaultClassLoaderCache(Cache<Key, ClassLoader> cache, ClassPathSnapshotter snapshotter) {
        this.cache = cache;
        this.snapshotter = snapshotter;
    }

    public ClassLoader get(final ClassLoader parent, final ClassPath classPath, @Nullable final FilteringClassLoader.Spec filterSpec) {
        try {
            ClassPathSnapshot s = snapshotter.snapshot(classPath);
            Key key = new Key(parent, s, filterSpec);
            return cache.get(key, new Callable<ClassLoader>() {
                public ClassLoader call() throws Exception {
                    if (filterSpec == null) {
                        return new URLClassLoader(classPath.getAsURLArray(), parent);
                    } else {
                        return new FilteringClassLoader(get(parent, classPath, null), filterSpec);
                    }
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
