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
package org.gradle.api.tasks;

import org.gradle.api.file.FileCollection;

/**
 * A {@code TaskInputs} represents the inputs for a task.
 */
public interface TaskInputs {
    /**
     * Returns the inputs files of this task.
     *
     * @return The input files. Returns an empty collection if this task has no input files.
     */
    FileCollection getInputFiles();

    /**
     * Registers some input files for this task. The given paths are evaluated as for {@link
     * org.gradle.api.Project#files(Object[])}.
     *
     * @param paths The input files.
     * @return this
     */
    TaskInputs inputFiles(Object... paths);
}
