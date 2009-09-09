/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.file;

import org.gradle.api.tasks.util.PatternFilterable;

import java.io.File;

/**
 * <p>A {@link FileTree} with a single base directory, which can be configured and modified.</p>
 *
 * <p>You can obtain a {@code ConfigurableFileTree} instance using {@link org.gradle.api.Project#fileTree(java.util.Map)}.</p>
 */
public interface ConfigurableFileTree extends FileTree, PatternFilterable {
    /**
     * Specifies base directory for this file tree using the given path. The path is evaluated as for {@link
     * org.gradle.api.Project#file(Object)}.
     *
     * @param dir The base directory.
     * @return this
     */
    ConfigurableFileTree from(Object dir);

    /**
     * Returns the base directory of this file tree.
     *
     * @return The base directory. Never returns null.
     */
    File getDir();

    /**
     * Specifies base directory for this file tree using the given path. The path is evaluated as for {@link
     * org.gradle.api.Project#file(Object)}.
     *
     * @param dir The base directory.
     * @return this
     */
    ConfigurableFileTree setDir(Object dir);
}
