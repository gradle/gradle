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

package org.gradle.api.internal.tasks;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TaskPropertyUtils {
    /**
     * Visits both properties declared via annotations on the properties of the task type as well as
     * properties declared via the runtime API ({@link org.gradle.api.tasks.TaskInputs} etc.).
     */
    // TODO Move this to some test fixture like TaskPropertyTestUtils
    @VisibleForTesting
    public static void visitProperties(PropertyWalker propertyWalker, TaskInternal task, PropertyVisitor visitor) {
        visitProperties(propertyWalker, task, TypeValidationContext.NOOP, visitor);
    }

    /**
     * Visits both properties declared via annotations on the properties of the task type as well as
     * properties declared via the runtime API ({@link org.gradle.api.tasks.TaskInputs} etc.).
     *
     * Reports errors and warnings to the given validation context.
     */
    public static void visitProperties(PropertyWalker propertyWalker, TaskInternal task, TypeValidationContext validationContext, PropertyVisitor visitor) {
        visitAnnotatedProperties(propertyWalker, task, validationContext, visitor);
        visitRegisteredProperties(task, visitor);
    }

    private static void visitRegisteredProperties(TaskInternal task, PropertyVisitor visitor) {
        task.getInputs().visitRegisteredProperties(visitor);
        task.getOutputs().visitRegisteredProperties(visitor);
        ((TaskDestroyablesInternal) task.getDestroyables()).visitRegisteredProperties(visitor);
        ((TaskLocalStateInternal) task.getLocalState()).visitRegisteredProperties(visitor);
        // build services declared via Task#usesService are not visited as there is no use case for that
    }

    static void visitAnnotatedProperties(PropertyWalker propertyWalker, TaskInternal task, TypeValidationContext validationContext, PropertyVisitor visitor) {
        propertyWalker.visitProperties(task, validationContext, visitor);
    }
}
