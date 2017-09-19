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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.TaskPropertyInfo;

public class TaskPropertyValue implements ValidatingValue {
    private final TaskPropertyInfo property;
    private final TaskInternal task;

    public TaskPropertyValue(TaskPropertyInfo property, TaskInternal task) {
        this.property = property;
        this.task = task;
    }

    @Override
    public Object call() {
        return property.getValue(task).getValue();
    }

    @Override
    public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
        property.getValue(task).validate(optional, valueValidator, context, TaskValidationContext.Severity.ERROR);
    }

    @Override
    public String toString() {
        return String.format("property (%s) for task '%s'", property, task.getName());
    }
}
