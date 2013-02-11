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
package org.gradle.api.artifacts.repositories;

import java.io.File;
import java.util.Set;

/**
 * A repository that looks into a number of directories for artifacts. The artifacts are expected to be located in the root of the specified directories.
 * The repository ignores any group/organization information specified in the dependency section of your build script. If you only use this kind of
 * resolver you can specify your dependencies like <code>:junit:4.8.1</code> instead of <code>junit:junit:4.8.1</code>.
 *
 * <p>To resolve a dependency, this resolver looks for one of the following files. It will return the first match it finds:
 *
 * <ul>
 *
 * <li>[artifact]-[version].[ext]
 * <li>[artifact]-[version]-[classifier].[ext]
 * <li>[artifact].[ext]
 * <li>[artifact]-[classifier].[ext]
 *
 * </ul>
 *
 * So, for example, to resolve <code>:junit:junit:4.8.1</code>, this repository will look for <code>junit-4.8.1.jar</code> and then <code>junit.jar</code>.
 */
public interface FlatDirectoryArtifactRepository extends ArtifactRepository {
    /**
     * Returns the directories where this repository will look for artifacts.
     *
     * @return The directories. Never null.
     */
    Set<File> getDirs();

    /**
     * Adds a directory where this repository will look for artifacts.
     *
     * <p>The provided value are evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param dir the directory
     */
    void dir(Object dir);

    /**
     * Adds some directories where this repository will look for artifacts.
     *
     * <p>The provided values are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param dirs the directories.
     */
    void dirs(Object... dirs);

    /**
     * Sets the directories where this repository will look for artifacts.
     *
     * <p>The provided values are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param dirs the directories.
     */
    void setDirs(Iterable<?> dirs);
}
