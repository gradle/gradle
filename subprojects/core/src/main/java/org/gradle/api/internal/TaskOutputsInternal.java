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

package org.gradle.api.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputs;

import java.util.SortedSet;

public interface TaskOutputsInternal extends TaskOutputs {

    Spec<? super TaskInternal> getUpToDateSpec();

    SortedSet<TaskOutputFilePropertySpec> getFileProperties();

    /**
     * Returns the output files recorded during the previous execution of the task.
     */
    FileCollection getPreviousOutputFiles();

    void setHistory(TaskExecutionHistory history);

    /**
     * Check if caching is explicitly enabled for the task outputs.
     */
    boolean isCacheEnabled();

    /**
     * Returns whether the task has declared any outputs.
     */
    boolean hasDeclaredOutputs();

    /**
     * Returns {@code false} if the task declares any multiple-output properties via {@link #files(Object...)},
     * {@literal @}{@link org.gradle.api.tasks.OutputFiles} or
     * {@literal @}{@link org.gradle.api.tasks.OutputDirectories}; or {@code true} otherwise.
     */
    boolean isCacheAllowed();
}
