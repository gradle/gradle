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
 * A {@code TaskOutputs} represents the outputs of a task.
 */
public interface TaskOutputs {
    /**
     * Returns true if this task can produce output files. Note that a task may be able to produce output files and
     * still have an empty set of output files.
     *
     * @return true if this task produces output files, otherwise false.
     */
    boolean getHasOutputFiles();

    /**
     * Returns the output files of this task.
     *
     * @return The output files. Returns an empty collection if this task has no output files.
     */
    FileCollection getFiles();

    /**
     * Registers some output files/directories for this task.
     *
     * @param paths The output files. The given paths are evaluated as for {@link org.gradle.api.Project#files(Object[])}.
     * @return this
     */
    TaskOutputs files(Object... paths);
}
