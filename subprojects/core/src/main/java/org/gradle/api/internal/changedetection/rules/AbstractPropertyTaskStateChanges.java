/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.List;
import java.util.Map;
import java.util.Set;

@NonNullApi
public abstract class AbstractPropertyTaskStateChanges extends SimpleTaskStateChanges {

    private final TaskExecution previous;
    private final TaskExecution current;
    private final String title;
    private final Task task;

    protected AbstractPropertyTaskStateChanges(TaskExecution previous, TaskExecution current, String title, Task task) {
        this.previous = previous;
        this.current = current;
        this.title = title;
        this.task = task;
    }

    protected abstract Map<String, ?> getProperties(TaskExecution execution);

    @Override
    protected void addAllChanges(final List<TaskStateChange> changes) {
        Set<String> currentNames = getProperties(current).keySet();
        Set<String> previousNames = getProperties(previous).keySet();

        for (String name : currentNames) {
            if (!previousNames.contains(name)) {
                changes.add(new DescriptiveChange("%s property '%s' has been added for %s", title, name, task));
            }
        }
        for (String name : previousNames) {
            if (!currentNames.contains(name)) {
                changes.add(new DescriptiveChange("%s property '%s' has been removed for %s", title, name, task));
            }
        }
    }
}
