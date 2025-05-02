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

import org.gradle.internal.HasInternalProtocol;

/**
 * Represents the files or directories that represent the local state of a {@link org.gradle.api.Task}.
 * The typical usage for local state is to store non-relocatable incremental analysis between builds.
 * Local state is removed whenever the task is loaded from cache.
 *
 * @since 4.3
 */
@HasInternalProtocol
public interface TaskLocalState {
    /**
     * Registers files and directories as local state of this task.
     *
     * @param paths The files that represent the local state. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    void register(Object... paths);
}
