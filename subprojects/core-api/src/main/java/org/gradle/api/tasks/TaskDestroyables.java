/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Represents the files or directories that a {@link org.gradle.api.Task} destroys (removes).
 *
 * @since 4.0
 */
@Incubating
@HasInternalProtocol
public interface TaskDestroyables {
    /**
     * Registers files or directories that this task destroys.
     *
     * @param paths The files or directories that will be destroyed. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @since 4.3
     */
    void register(Object... paths);

    /**
     * Registers some files that this task destroys.
     *
     * @param paths The files that will be destroyed. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @deprecated Use {@link #register(Object...)} instead.
     */
    @Deprecated
    void files(Object... paths);

    /**
     * Registers a file or directory that this task destroys.
     *
     * @param path A file that will be destroyed. The given path is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @deprecated Use {@link #register(Object...)} instead.
     */
    @Deprecated
    void file(Object path);
}
