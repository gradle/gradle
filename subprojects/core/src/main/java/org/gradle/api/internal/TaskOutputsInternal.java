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

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputs;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

@NonNullApi
public interface TaskOutputsInternal extends TaskOutputs {

    Spec<? super TaskInternal> getUpToDateSpec();

    ImmutableSortedSet<TaskOutputFilePropertySpec> getFileProperties();

    TaskOutputFilePropertyBuilder file(ValidatingValue path);

    TaskOutputFilePropertyBuilder dir(ValidatingValue path);

    TaskOutputFilePropertyBuilder files(ValidatingValue paths);

    TaskOutputFilePropertyBuilder dirs(ValidatingValue paths);

    /**
     * Returns the output files and directories recorded during the previous execution of the task.
     */
    Set<File> getPreviousOutputFiles();

    void setHistory(@Nullable TaskExecutionHistory history);

    /**
     * Yields information about the cacheability of the outputs.
     */
    TaskOutputCachingState getCachingState();

    /**
     * Returns whether the task has declared any outputs.
     */
    boolean hasDeclaredOutputs();

    void validate(TaskValidationContext context);
}
