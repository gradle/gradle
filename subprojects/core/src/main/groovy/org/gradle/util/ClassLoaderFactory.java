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
package org.gradle.util;

import java.net.URL;

public interface ClassLoaderFactory {
    /**
     * Creates a ClassLoader implementation which has only the classes from the specified URLs visible and the Java API visible.
     * @param urls The URLs
     * @return The ClassLoader
     */
    ClassLoader createIsolatedClassLoader(Iterable<URL> urls);

    /**
     * Creates a ClassLoader implementation which has, by default, only the classes from the Java API visible, but which can allow access
     * to selected classes from the given parent ClassLoader.
     *
     * @param parent the parent ClassLoader
     * @return The ClassLoader
     */
    FilteringClassLoader createFilteringClassLoader(ClassLoader parent);
}
