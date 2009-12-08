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
 * <p>A {@code TaskInputs} represents the inputs for a task.</p>
 *
 * <p>You can obtain a {@code TaskInputs} instance using {@link org.gradle.api.Task#getInputs()}.</p>
 */
public interface TaskInputs {
    /**
     * Returns true if this task can consume input files. Note that a task may be able to consume input files and still
     * have an empty set of input files.
     *
     * @return true if this task consumes input files, otherwise false.
     */
    boolean getHasInputFiles();

    /**
     * Returns the input files of this task.
     *
     * @return The input files. Returns an empty collection if this task has no input files.
     */
    FileCollection getFiles();

    /**
     * Registers some input files for this task.
     *
     * @param paths The input files. The given paths are evaluated as for {@link org.gradle.api.Project#files(Object[])}.
     * @return this
     */
    TaskInputs files(Object... paths);

    /**
     * Registers an input directory hierarchy. All files found under the given directory are treated as input files for
     * this task.
     *
     * @param dirPath The directory. The path is evaluated as for {@link org.gradle.api.Project#file(Object)}.
     * @return this
     */
    TaskInputs dir(Object dirPath);
}
