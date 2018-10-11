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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskPropertySpec;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.util.Map;

/**
 * A view of the properties of a task.
 *
 * This includes inputs, outputs, destroyables and local state properties.
 *
 * Once created, the view is immutable and registering additional or changing existing task properties will not be detected.
 *
 * Created by {@link DefaultTaskProperties#resolve(PropertyWalker, PathToFileResolver, TaskInternal)}.
 */
@NonNullApi
public interface TaskProperties {
    /**
     * Returns all properties.
     */
    Iterable<? extends TaskPropertySpec> getProperties();

    /**
     * A factory for the input properties.
     *
     * Calling `create` on the factory results in evaluating the input properties and gathering them into a Map.
     */
    Factory<Map<String, Object>> getInputPropertyValues();

    /**
     * Input file properties.
     *
     * It is guaranteed that all the {@link TaskInputFilePropertySpec}s have a name and that the names are unique.
     */
    ImmutableSortedSet<TaskInputFilePropertySpec> getInputFileProperties();

    /**
     * The input files.
     */
    FileCollection getInputFiles();

    /**
     * Whether there are source files.
     *
     * Source files are {@link TaskInputFilePropertySpec}s where {@link TaskInputFilePropertySpec#isSkipWhenEmpty()} returns true.
     */
    boolean hasSourceFiles();

    /**
     * The source files.
     */
    FileCollection getSourceFiles();

    /**
     * Output file properties.
     *
     * It is guaranteed that all the {@link TaskOutputFilePropertySpec}s have a name and that the names are unique.
     */
    ImmutableSortedSet<TaskOutputFilePropertySpec> getOutputFileProperties();

    /**
     * The output files.
     */
    FileCollection getOutputFiles();

    /**
     * Whether output properties have been declared.
     */
    boolean hasDeclaredOutputs();

    /**
     * The files that represent the local state.
     */
    FileCollection getLocalStateFiles();

    /**
     * The files that are destroyed.
     */
    FileCollection getDestroyableFiles();

    /**
     * Validations for the properties.
     */
    void validate(TaskValidationContext validationContext);
}
