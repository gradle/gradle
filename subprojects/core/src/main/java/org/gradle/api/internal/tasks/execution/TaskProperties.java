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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskValidationContext;

import java.util.Map;

public interface TaskProperties {

    boolean hasSourceFiles();

    FileCollection getInputFiles();

    ImmutableSortedSet<TaskOutputFilePropertySpec> getOutputFileProperties();

    FileCollection getOutputFiles();

    FileCollection getSourceFiles();

    ImmutableSortedSet<TaskInputFilePropertySpec> getInputFileProperties();

    void validate(TaskValidationContext validationContext);

    boolean hasDeclaredOutputs();

    Map<String, Object> getInputProperties();

    FileCollection getLocalStateFiles();
}
