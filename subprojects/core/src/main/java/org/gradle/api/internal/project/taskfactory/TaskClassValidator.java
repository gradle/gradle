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
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskPropertyValue;

import java.util.List;
import java.util.Set;

@NonNullApi
public class TaskClassValidator {
    private final ImmutableSortedSet<TaskPropertyInfo> annotatedProperties;
    private final ImmutableList<TaskClassValidationMessage> validationMessages;
    private final boolean cacheable;

    public TaskClassValidator(Set<TaskPropertyInfo> annotatedProperties, List<TaskClassValidationMessage> validationMessages, boolean cacheable) {
        this.annotatedProperties = ImmutableSortedSet.copyOf(annotatedProperties);
        this.validationMessages = ImmutableList.copyOf(validationMessages);
        this.cacheable = cacheable;
    }

    public void addInputsAndOutputs(final TaskInternal task) {
        for (TaskPropertyInfo property : annotatedProperties) {
            property.getConfigureAction().update(task, new TaskPropertyValue(property, task));
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
