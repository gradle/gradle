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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.internal.changes.TaskStateChangeVisitor;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.util.Map;

class InputPropertyValueTaskStateChanges implements TaskStateChanges {
    private final TaskInternal task;
    private final ImmutableMap<String, String> changed;

    public InputPropertyValueTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution.getInputProperties();
        ImmutableMap.Builder<String, String> changedBuilder = ImmutableMap.builder();
        ImmutableSortedMap<String, ValueSnapshot> currentInputProperties = currentExecution.getInputProperties();
        for (Map.Entry<String, ValueSnapshot> entry : currentInputProperties.entrySet()) {
            String propertyName = entry.getKey();
            ValueSnapshot currentSnapshot = entry.getValue();
            ValueSnapshot previousSnapshot = previousInputProperties.get(propertyName);
            if (previousSnapshot != null) {
                if (!currentSnapshot.equals(previousSnapshot)) {
                    changedBuilder.put(
                        propertyName,
                        currentSnapshot instanceof ImplementationSnapshot ? "Implementation" : "Value");
                }
            }
        }
        this.changed = changedBuilder.build();
        this.task = task;
    }

    @Override
    public boolean accept(TaskStateChangeVisitor visitor) {
        for (Map.Entry<String, String> entry : changed.entrySet()) {
            String propertyName = entry.getKey();
            String changeType = entry.getValue();
            if (!visitor.visitChange(new DescriptiveChange("%s of input property '%s' has changed for %s", changeType, propertyName, task))) {
                return false;
            }
        }
        return true;
    }
}
