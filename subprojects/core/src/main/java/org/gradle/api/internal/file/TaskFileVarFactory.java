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

package org.gradle.api.internal.file;

import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.MinimalFileSet;

public interface TaskFileVarFactory {
    /**
     * Creates a {@link ConfigurableFileCollection} that can be used as a task input.
     *
     * <p>The implementation may apply caching to the result, so that the matching files are calculated during file snapshotting and the result cached in memory for when it is queried again, either during task action execution or in order to calculate some other task input value.
     *
     * <p>Use this collection only for those files that are not expected to change during task execution, such as task inputs.
     */
    ConfigurableFileCollection newInputFileCollection(Task consumer);

    /**
     * Creates a {@link FileCollection} that represents some task input that is calculated from one or more other file collections.
     *
     * <p>The implementation applies caching to the result, so that the matching files are calculated during file snapshotting and the result cached in memory for when it is queried again, either during task action execution or in order to calculate some other task input value.
     *
     * <p>Use this collection only for those files that are not expected to change during task execution, such as task inputs.
     */
    FileCollection newCalculatedInputFileCollection(Task consumer, MinimalFileSet calculatedFiles, FileCollection... inputs);
}
