/*
 * Copyright 2007, 2008 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultTaskDependency implements TaskDependency {
    private final Set<Object> values = new HashSet<Object>();

    public Set<Task> getDependencies(Task task) {
        Set<Task> result = new HashSet<Task>();
        for (Object dependency : values) {
            if (dependency instanceof Task) {
                result.add((Task) dependency);
            } else if (dependency instanceof TaskDependency) {
                result.addAll(((TaskDependency) dependency).getDependencies(task));
            } else if (dependency instanceof Closure) {
                Closure closure = (Closure) dependency;
                Object closureResult = closure.call(task);
                if (closureResult instanceof Task) {
                    result.add((Task) closureResult);
                } else {
                    result.addAll((Collection<? extends Task>) closureResult);
                }
            } else {
                String path = dependency.toString();
                result.add(task.getProject().getTasks().getByPath(path));
            }
        }
        return result;
    }

    public Set<Object> getValues() {
        return values;
    }

    public void setValues(Set<?> values) {
        this.values.clear();
        add(values);
    }

    public DefaultTaskDependency add(Object... values) {
        List<Object> flattened = new ArrayList<Object>();
        GUtil.flatten(values, flattened);
        for (Object value : flattened) {
            addValue(value);
        }
        return this;
    }

    private void addValue(Object dependency) {
        if (!GUtil.isTrue(dependency)) {
            throw new InvalidUserDataException("A dependency must not be empty");
        }
        this.values.add(dependency);
    }
}
