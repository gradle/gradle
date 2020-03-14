/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.execution.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;

/**
 * Allows the registration of {@link TaskInputsListener task inputs listeners}.
 */
public interface TaskInputsListeners {

    /**
     * Registers the listener with the build and returns an {@link AutoCloseable} registration,
     * the listener is unregistered once the returned registration is closed.
     */
    AutoCloseable addListener(TaskInputsListener listener);

    void removeListener(TaskInputsListener listener);

    void broadcastFileSystemInputsOf(TaskInternal task, FileCollectionInternal fileSystemInputs);
}
