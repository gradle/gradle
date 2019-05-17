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

import javax.annotation.Nullable;
import java.util.Map;

/**
 * <p>A {@code TaskInputs} represents the inputs for a task.</p>
 *
 * <p>You can obtain a {@code TaskInputs} instance using {@link org.gradle.api.Task#getInputs()}.</p>
 */
@HasInternalProtocol
public interface TaskInputs extends CompatibilityAdapterForTaskInputs {
    /**
     * Returns true if this task has declared the inputs that it consumes.
     *
     * @return true if this task has declared any inputs.
     */
    boolean getHasInputs();

    /**
     * Returns the input files of this task.
     *
     * @return The input files. Returns an empty collection if this task has no input files.
     */
    FileCollection getFiles();

    /**
     * Registers some input files for this task.
     *
     * @param paths The input files. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     * @return a property builder to further configure the property.
     */
    TaskInputFilePropertyBuilder files(Object... paths);

    /**
     * Registers some input file for this task.
     *
     * @param path The input file. The given path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * @return a property builder to further configure the property.
     */
    TaskInputFilePropertyBuilder file(Object path);

    /**
     * Registers an input directory hierarchy. All files found under the given directory are treated as input files for
     * this task.
     *
     * @param dirPath The directory. The path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * @return a property builder to further configure the property.
     */
    TaskInputFilePropertyBuilder dir(Object dirPath);

    /**
     * Returns the set of input properties for this task.
     *
     * @return The properties.
     */
    Map<String, Object> getProperties();

    /**
     * <p>Registers an input property for this task. This value is persisted when the task executes, and is compared
     * against the property value for later invocations of the task, to determine if the task is up-to-date.</p>
     *
     * <p>The given value for the property must be Serializable, so that it can be persisted. It should also provide a
     * useful {@code equals()} method.</p>
     *
     * <p>You can specify a closure or {@code Callable} as the value of the property. In which case, the closure or
     * {@code Callable} is executed to determine the actual property value.</p>
     *
     * @param name The name of the property. Must not be null.
     * @param value The value for the property. Can be null.
     */
    @Override
    TaskInputPropertyBuilder property(String name, @Nullable Object value);

    /**
     * Registers a set of input properties for this task. See {@link #property(String, Object)} for details.
     *
     * <p><strong>Note:</strong> do not use the return value to chain calls.
     * Instead always use call via {@link org.gradle.api.Task#getInputs()}.</p>
     *
     * @param properties The properties.
     */
    TaskInputs properties(Map<String, ?> properties);

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
