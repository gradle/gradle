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

import org.gradle.api.Buildable;
import org.gradle.api.tasks.util.PatternFilterable;

/**
 * <p>A configurable {@link FileTree}.</p>
 *
 * <p>You can obtain a {@code ConfigurableFileTree} instance by calling {@link org.gradle.api.Project#fileTree(java.util.Map)}.</p>
 */
public interface ConfigurableFileTree extends FileTree, ConfigurableFileCollection, PatternFilterable, Buildable {
    /**
     * Specifies base directory for this file tree using the given path. The path is evaluated as per {@link
     * org.gradle.api.Project#file(Object)}.
     *
     * @param dir The base directory.
     * @return this
     */
    @Override
    ConfigurableFileTree from(Object... dir);

    @Override
    ConfigurableFileTree convention(Iterable<?> paths);

    @Override
    ConfigurableFileTree convention(Object... paths);

    /**
     * Sets the tasks which build the files of this collection.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    @Override
    ConfigurableFileTree setBuiltBy(Iterable<?> tasks);

    /**
     * Registers some tasks which build the files of this collection.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    @Override
    ConfigurableFileTree builtBy(Object... tasks);
}
