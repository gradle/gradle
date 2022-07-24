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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;

@NonNullApi
public class TaskPropertyUtils {
    /**
     * Visits both properties declared via annotations on the properties of the task type as well as
     * properties declared via the runtime API ({@link org.gradle.api.tasks.TaskInputs} etc.).
     */
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
        propertyWalker.visitProperties(task, validationContext, visitor);
        task.getInputs().visitRegisteredProperties(visitor);
        task.getOutputs().visitRegisteredProperties(visitor);
        ((TaskDestroyablesInternal) task.getDestroyables()).visitRegisteredProperties(visitor);
        ((TaskLocalStateInternal) task.getLocalState()).visitRegisteredProperties(visitor);
    }

    /**
     * Checks if the given string can be used as a property name.
     *
     * @throws IllegalArgumentException if given name is an empty string.
     */
    public static String checkPropertyName(String propertyName) {
        if (propertyName.isEmpty()) {
            throw new IllegalArgumentException("Property name must not be empty string");
        }
        return propertyName;
    }
}
