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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.execution.TaskValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class TaskClassValidator implements TaskValidator, Action<Task>, Describable {
    private final ImmutableSortedSet<TaskPropertyInfo> annotatedProperties;
    private final ImmutableList<TaskClassValidationMessage> validationMessages;
    private final boolean cacheable;

    public TaskClassValidator(Set<TaskPropertyInfo> annotatedProperties, List<TaskClassValidationMessage> validationMessages, boolean cacheable) {
        this.annotatedProperties = ImmutableSortedSet.copyOf(annotatedProperties);
        this.validationMessages = ImmutableList.copyOf(validationMessages);
        this.cacheable = cacheable;
    }

    @Override
    public void execute(Task task) {
    }

    public void addInputsAndOutputs(final TaskInternal task) {
        task.addValidator(this);
        for (TaskPropertyInfo property : annotatedProperties) {
            property.getConfigureAction().update(task, new FutureValue(property, task));
        }
    }

    @Override
    public String getDisplayName() {
        return "Validate task input";
    }

    private static class FutureValue implements Callable<Object> {
        private final TaskPropertyInfo property;
        private final TaskInternal task;

        private FutureValue(TaskPropertyInfo property, TaskInternal task) {
            this.property = property;
            this.task = task;
        }

        @Override
        public Object call() throws Exception {
            return property.getValue(task).getValue();
        }

        @Override
        public String toString() {
            return String.format("property (%s) for task '%s'", property, task.getName());
        }
    }

    @Override
    public void validate(TaskInternal task, Collection<String> messages) {
        List<TaskPropertyValue> propertyValues = new ArrayList<TaskPropertyValue>();
        for (TaskPropertyInfo property : annotatedProperties) {
            propertyValues.add(property.getValue(task));
        }
        for (TaskPropertyValue propertyValue : propertyValues) {
            propertyValue.checkNotNull(messages);
        }
        for (TaskPropertyValue propertyValue : propertyValues) {
            propertyValue.checkValid(messages);
        }
    }

    public boolean hasAnythingToValidate() {
        return !annotatedProperties.isEmpty();
    }

    public ImmutableSortedSet<TaskPropertyInfo> getAnnotatedProperties() {
        return annotatedProperties;
    }

    public ImmutableList<TaskClassValidationMessage> getValidationMessages() {
        return validationMessages;
    }

    public boolean isCacheable() {
        return cacheable;
    }
}
