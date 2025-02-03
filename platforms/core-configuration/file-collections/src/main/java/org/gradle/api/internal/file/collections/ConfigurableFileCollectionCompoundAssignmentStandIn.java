/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;

/**
 * This class acts as a replacement to call {@code +} on when evaluating {@code ConfigurableFileCollection += <RHS>} expressions in Groovy code.
 *
 * <b>This is a hidden public API</b>. Compiling Groovy code that depends on Gradle API may end up emitting references to methods of this class.
 */
public final class ConfigurableFileCollectionCompoundAssignmentStandIn {
    private final DefaultConfigurableFileCollection lhs;
    private final TaskDependencyFactory taskDependencyFactory;

    ConfigurableFileCollectionCompoundAssignmentStandIn(DefaultConfigurableFileCollection lhs, TaskDependencyFactory taskDependencyFactory) {
        this.lhs = lhs;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    public FileCollection plus(FileCollection rhs) {
        return new FileCollectionCompoundAssignmentResult(taskDependencyFactory, lhs, (FileCollectionInternal) rhs);
    }
}
