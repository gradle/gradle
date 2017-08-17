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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.ValueSnapshot;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class InputPropertiesTaskStateChanges extends SimpleTaskStateChanges {
    private final TaskInternal task;
    private final Set<String> removed;
    private final Set<String> changed;
    private final Set<String> added;

    public InputPropertiesTaskStateChanges(@Nullable TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : previousExecution.getInputProperties();
        removed = new HashSet<String>(previousInputProperties.keySet());
        changed = new HashSet<String>();
        added = new HashSet<String>();
        ImmutableSortedMap<String, ValueSnapshot> currentInputProperties = currentExecution.getInputProperties();
        for (Map.Entry<String, ValueSnapshot> entry : currentInputProperties.entrySet()) {
            String propertyName = entry.getKey();
            ValueSnapshot currentSnapshot = entry.getValue();
            removed.remove(propertyName);
            ValueSnapshot previousSnapshot = previousInputProperties.get(propertyName);
            if (previousSnapshot == null) {
                added.add(propertyName);
            } else {
                if (!currentSnapshot.equals(previousSnapshot)) {
                    changed.add(propertyName);
                }
            }
        }
        this.task = task;
    }

    @Override
    protected void addAllChanges(final List<TaskStateChange> changes) {
        for (String propertyName : changed) {
            changes.add(new DescriptiveChange("Value of input property '%s' has changed for %s", propertyName, task));
        }
        for (String propertyName : added) {
            changes.add(new DescriptiveChange("Input property '%s' has been added for %s", propertyName, task));
        }
        for (String propertyName : removed) {
            changes.add(new DescriptiveChange("Input property '%s' has been removed for %s", propertyName, task));
        }
    }
}
