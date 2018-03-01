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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.ImplementationSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.ValueSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;

class InputPropertyValueTaskStateChanges extends SimpleTaskStateChanges {
    private final TaskInternal task;
    private final Set<String> valueChanges;
    private final Set<String> implementationChanges;

    public InputPropertyValueTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution.getInputProperties();
        ImmutableSet.Builder<String> valueChangesBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> implementationChangesBuilder = ImmutableSet.builder();
        ImmutableSortedMap<String, ValueSnapshot> currentInputProperties = currentExecution.getInputProperties();
        for (Map.Entry<String, ValueSnapshot> entry : currentInputProperties.entrySet()) {
            String propertyName = entry.getKey();
            ValueSnapshot currentSnapshot = entry.getValue();
            ValueSnapshot previousSnapshot = previousInputProperties.get(propertyName);
            if (previousSnapshot != null) {
                if (!currentSnapshot.equals(previousSnapshot)) {
                    if (currentSnapshot instanceof ImplementationSnapshot) {
                        implementationChangesBuilder.add(propertyName);
                    } else {
                        valueChangesBuilder.add(propertyName);
                    }
                }
            }
        }
        valueChanges = valueChangesBuilder.build();
        implementationChanges = implementationChangesBuilder.build();
        this.task = task;
    }

    @Override
    protected void addAllChanges(final List<TaskStateChange> changes) {
        for (String propertyName : implementationChanges) {
            changes.add(new DescriptiveChange("Implementation of input property '%s' has changed for %s", propertyName, task));
        }
        for (String propertyName : valueChanges) {
            changes.add(new DescriptiveChange("Value of input property '%s' has changed for %s", propertyName, task));
        }
    }
}
