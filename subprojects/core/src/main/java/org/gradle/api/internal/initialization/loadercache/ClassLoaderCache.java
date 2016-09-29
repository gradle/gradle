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

import com.google.common.hash.HashCode;
import org.gradle.api.Nullable;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.ClassPath;

public interface ClassLoaderCache {

    int size();

    /**
     * Returns an existing classloader from the cache, or creates it if it cannot be found.
     * @param id the ID of the classloader.
     * @param classPath the classpath to use to create the classloader.
     * @param parent the parent of the classloader.
     * @param filterSpec the filtering to use on the classpath.
     * @return the classloader.
     */
    ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec);

    /**
     * Returns an existing classloader from the cache, or creates it if it cannot be found.
     * @param id the ID of the classloader.
     * @param classPath the classpath to use to create the classloader.
     * @param parent the parent of the classloader.
     * @param filterSpec the filtering to use on the classpath.
     * @param overrideHashCode the returned classloader should use the given hash, or the hash of the classpath if the parameter is {@code null}.
     * @return the classloader.
     */
    ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec, HashCode overrideHashCode);

    void remove(ClassLoaderId id);

}
