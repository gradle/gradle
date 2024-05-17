/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.specs.Spec;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;

/**
 * An immutable classpath.
 */
public interface ClassPath {

    ClassPath EMPTY = new DefaultClassPath();

    /**
     * Returns {@code true} if this class path is empty, {@code false} otherwise.
     *
     * @return {@code true} if this class path is empty
     */
    boolean isEmpty();

    /**
     * Returns the list of URIs of the classpath entries (JARs or class directories) that this classpath consists of. The order is the classpath search order.
     *
     * @return the list of URIs of the classpath entries
     */
    List<URI> getAsURIs();

    /**
     * Returns the list of the classpath entries (JARs or class directories) that this classpath consists of. The order is the classpath search order.
     *
     * @return the list of the classpath entries
     */
    List<File> getAsFiles();

    /**
     * Returns the list of URLs of the classpath entries (JARs or class directories) that this classpath consists of. The order is the classpath search order.
     *
     * @return the list of the classpath entries
     */
    List<URL> getAsURLs();

    /**
     * Returns the array of URLs of the classpath entries (JARs or class directories) that this classpath consists of. The order is the classpath search order.
     *
     * @return the array of the classpath entries
     * @see #getAsURLs()
     */
    URL[] getAsURLArray();

    /**
     * Returns a new classpath with entries from the given {@code classPath} appended. Duplicate entries are not appended.
     *
     * @param classPath the list of files to append
     * @return the new classpath
     */
    ClassPath plus(Collection<File> classPath);

    /**
     * Returns a new classpath with entries from the given {@code classPath} appended. Duplicate entries are not appended.
     *
     * @param classPath the classpath to append
     * @return the new classpath
     */
    ClassPath plus(ClassPath classPath);

    /**
     * Returns a new classpath without entries matching the {@code filter}.
     *
     * @param filter the predicate to match entries to remove
     * @return the new classpath
     */
    ClassPath removeIf(Spec<? super File> filter);
}
