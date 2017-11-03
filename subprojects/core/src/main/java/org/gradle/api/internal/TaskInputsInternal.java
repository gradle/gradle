/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.tasks.TaskInputFilePropertyBuilderInternal;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;

public interface TaskInputsInternal extends TaskInputs {
    ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties();

    TaskInputFilePropertyBuilderInternal registerFiles(ValidatingValue paths);

    TaskInputFilePropertyBuilderInternal registerFile(ValidatingValue value);

    TaskInputFilePropertyBuilderInternal registerDir(ValidatingValue dirPath);

    TaskInputPropertyBuilder registerProperty(String name, ValidatingValue value);

    TaskInputPropertyBuilder registerNested(String name, ValidatingValue value);

    void validate(TaskValidationContext context);
}
