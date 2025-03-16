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

import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

@UsedByScanPlugin("test-retry")
@ServiceScope(Scope.UserHome.class)
public interface ClassLoaderCache {

    /**
     * Returns an existing classloader from the cache, or creates it if it cannot be found.
     *
     * @param id the ID of the classloader.
     * @param classPath the classpath to use to create the classloader.
     * @param parent the parent of the classloader.
     * @param filterSpec the filtering to use on the classpath.
     * @return the classloader.
     */
    ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, FilteringClassLoader.@Nullable Spec filterSpec);

    /**
     * Returns an existing classloader from the cache, or creates it if it cannot be found.
     *
     * @param id the ID of the classloader.
     * @param classPath the classpath to use to create the classloader.
     * @param parent the parent of the classloader.
     * @param filterSpec the filtering to use on the classpath.
     * @param implementationHash a hash that represents the contents of the classpath. Can be {@code null}, in which case the hash is calculated from the provided classpath
     * @return the classloader.
     */
    ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, FilteringClassLoader.@Nullable Spec filterSpec, @Nullable HashCode implementationHash);

    /**
     * Adds or replaces a classloader. This should be called to register specialized classloaders that belong to the hierarchy, so they can be cleaned up as required.
     *
     * @param id the ID of the classloader.
     * @return the classloader.
     */
    ClassLoader createIfAbsent(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, Function<ClassLoader, ClassLoader> factoryFunction, @Nullable HashCode implementationHash);

    /**
     * Discards the given classloader.
     */
    void remove(ClassLoaderId id);
}
