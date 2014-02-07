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

package org.gradle.api.internal.initialization;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
        private final ClassPath classPath;
        private final FilteringClassLoader.Spec filterSpec;

        private Key(ClassLoader parent, ClassPath classPath, FilteringClassLoader.Spec filterSpec) {
            this.parent = parent;
            this.classPath = classPath;
            this.filterSpec = filterSpec;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (!classPath.equals(key.classPath)) {
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

        @Override
        public int hashCode() {
            int result = parent.hashCode();
            result = 31 * result + classPath.hashCode();
            result = 31 * result + (filterSpec != null ? filterSpec.hashCode() : 0);
            return result;
        }
    }

    private final Cache<Key, ClassLoader> cache;

    public DefaultClassLoaderCache() {
        this(CacheBuilder.newBuilder().<Key, ClassLoader>build());
    }

    public DefaultClassLoaderCache(Cache<Key, ClassLoader> cache) {
        this.cache = cache;
    }

    public ClassLoader get(final ClassLoader parent, final ClassPath classPath, @Nullable final FilteringClassLoader.Spec filterSpec) {
        try {
            return cache.get(new Key(parent, classPath, filterSpec), new Callable<ClassLoader>() {
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
