/*
 * Copyright 2011 the original author or authors.
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

import java.net.URI;
import java.util.List;

public interface ClassLoaderFactory {
    /**
     * Creates a ClassLoader implementation which has only the classes from the specified URIs and the Java API visible.
     */
    ClassLoader createIsolatedClassLoader(ClassPath classPath);

    /**
     * Creates a ClassLoader implementation which has only the classes from the specified URIs and the Java API visible.
     */
    ClassLoader createIsolatedClassLoader(Iterable<URI> uris);

    /**
     * Creates a ClassLoader implementation which has, by default, only the classes from the Java API visible, but which can allow access
     * to selected classes from the given parent ClassLoader.
     *
     * @param parent the parent ClassLoader
     * @return The ClassLoader
     */
    FilteringClassLoader createFilteringClassLoader(ClassLoader parent);

    /**
     * Creates a ClassLoader from its spec.
     */
    ClassLoader createClassLoader(ClassLoaderSpec spec, List<? extends ClassLoader> parents);
}
