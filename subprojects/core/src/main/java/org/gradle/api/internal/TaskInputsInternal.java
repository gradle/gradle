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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.TaskInputs;

import java.util.Map;

public interface TaskInputsInternal extends TaskInputs {

    /**
     * Calls the corresponding visitor methods for all inputs added via the runtime API.
     */
    void visitRegisteredProperties(PropertyVisitor visitor);

    ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties();

    GetFilePropertiesVisitor getFilePropertiesVisitor();

    GetInputPropertiesVisitor getInputPropertiesVisitor();

    interface GetFilePropertiesVisitor extends PropertyVisitor {
        ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties();
        FileCollection getFiles();
        FileCollection getSourceFiles();

        boolean hasSourceFiles();
    }

    interface GetInputPropertiesVisitor extends PropertyVisitor {
        Map<String, Object> getProperties();
    }

    /**
     * Called prior to the use of these inputs during task execution. The implementation may finalize and cache whatever state is required to efficiently calculate the snapshot, cache key and values of these inputs during task execution.
     */
    void prepareValues();

    /**
     * Called after task execution has completed, regardless of task outcome. The implementation may release whatever state was cached during task execution.
     */
    void cleanupValues();
}
