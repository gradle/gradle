/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.internal.HasInternalProtocol;

import java.util.Map;

/**
 * <p>A {@code TaskInputs} represents the inputs for a task.</p>
 *
 * <p>You can obtain a {@code TaskInputs} instance using {@link org.gradle.api.Task#getInputs()}.</p>
 */
@HasInternalProtocol
public interface TaskInputs extends InputPropertyRegistration, CompatibilityAdapterForTaskInputs {
    /**
     * Returns the input files of this task.
     *
     * @return The input files. Returns an empty collection if this task has no input files.
     */
    FileCollection getFiles();

    /**
     * Returns the set of input properties for this task.
     *
     * @return The properties.
     */
    Map<String, Object> getProperties();

    /**
     * Returns true if this task has declared that it accepts source files.
     *
     * @return true if this task has source files, false if not.
     */
    boolean getHasSourceFiles();

    /**
     * Returns the set of source files for this task. These are the subset of input files which the task actually does work on.
     * A task is skipped if it has declared it accepts source files, and this collection is empty.
     *
     * @return The set of source files for this task.
     */
    FileCollection getSourceFiles();
}
