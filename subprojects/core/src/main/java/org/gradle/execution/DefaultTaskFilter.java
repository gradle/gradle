/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DefaultTaskFilter implements TaskFilter {

    private final List<String> filters = new LinkedList<>();
    private final List<TaskFilterListener> listeners = new LinkedList<>();

    private void notifyListeners() {
        for (TaskFilterListener listener : listeners) {
            listener.onFilter();
        }
    }

    @Override
    public void addListener(TaskFilterListener listener) {
        listeners.add(listener);
        listener.onFilter();
    }

    @Override
    public void excludeTaskNames(Set<String> excludedTaskNames) {
        if (!excludedTaskNames.isEmpty()) {
            filters.addAll(excludedTaskNames);
            notifyListeners();
        }
    }

    @Override
    public Spec<Task> toSpec(TaskSelector taskSelector) {
        List<Spec<Task>> tasks = new LinkedList<>();
        for (String filteredTask : filters) {
            tasks.add(taskSelector.getFilter(filteredTask));
        }
        return Specs.intersect(tasks);
    }
}
