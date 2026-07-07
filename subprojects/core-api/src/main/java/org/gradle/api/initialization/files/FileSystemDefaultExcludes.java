/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.initialization.files;

import org.gradle.api.Incubating;
import org.gradle.api.initialization.Settings;

/**
 * Configures the {@linkplain Settings#getFileSystemDefaultExcludes() default file-system exclude patterns}
 * used when scanning the file system for file collections (copy, archive, file collections, etc.).
 *
 * <p>Obtained via {@link Settings#fileSystemDefaultExcludes(org.gradle.api.Action)}. Operations are applied,
 * in order, on top of Gradle's built-in defaults (entries for common version-control directories such as
 * <code>**&#47;.git</code> and OS metadata such as <code>**&#47;.DS_Store</code>).</p>
 *
 * @since 9.7.0
 */
@Incubating
public interface FileSystemDefaultExcludes {

    /**
     * Adds the given patterns to the default excludes, on top of Gradle's built-in defaults.
     *
     * @param patterns the Ant-style patterns to add
     * @since 9.7.0
     */
    void add(String... patterns);

    /**
     * Removes the given patterns from the default excludes, for example a built-in default such as
     * <code>**&#47;.gitignore</code>. Removing a pattern that is not currently present has no effect.
     *
     * @param patterns the Ant-style patterns to remove
     * @since 9.7.0
     */
    void remove(String... patterns);

    /**
     * Removes all default excludes, so that no files are excluded by default.
     *
     * @since 9.7.0
     */
    void clear();
}
