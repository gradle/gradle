/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Buildable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;

import java.util.*;
import java.util.concurrent.Callable;

public class DefaultTaskDependency extends AbstractTaskDependency {
    private static final TaskResolver FAILING_RESOLVER = new TaskResolver() {
        public Task resolveTask(Object path) {
            throw new IllegalArgumentException(String.format("Cannot convert %s to a task.", path));
        }
    };
    private final Set<Object> values = new HashSet<Object>();
    private final TaskResolver resolver;

    public DefaultTaskDependency() {
        this(null);
    }

    public DefaultTaskDependency(TaskResolver resolver) {
        this.resolver = resolver == null ? FAILING_RESOLVER : resolver;
    }

    public void resolve(TaskDependencyResolveContext context) {
        LinkedList<Object> queue = new LinkedList<Object>(values);
        while (!queue.isEmpty()) {
            Object dependency = queue.removeFirst();
            if (dependency instanceof Buildable) {
                context.add(dependency);
            } else if (dependency instanceof Task) {
                context.add(dependency);
            } else if (dependency instanceof TaskDependency) {
                context.add(dependency);
            } else if (dependency instanceof Closure) {
                Closure closure = (Closure) dependency;
                Object closureResult = closure.call(context.getTask());
                if (closureResult != null) {
                    queue.add(0, closureResult);
                }
            } else if (dependency instanceof Iterable) {
                Iterable<?> iterable = (Iterable) dependency;
                queue.addAll(0, GUtil.addToCollection(new ArrayList<Object>(), iterable));
            } else if (dependency instanceof Map) {
                Map<?, ?> map = (Map) dependency;
                queue.addAll(0, map.values());
            } else if (dependency instanceof Object[]) {
                Object[] array = (Object[]) dependency;
                queue.addAll(0, Arrays.asList(array));
            } else if (dependency instanceof Callable) {
                Callable callable = (Callable) dependency;
                Object callableResult;
                try {
                    callableResult = callable.call();
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (callableResult != null) {
                    queue.add(0, callableResult);
                }
            } else {
                context.add(resolver.resolveTask(dependency));
            }
        }
    }

    public Set<Object> getValues() {
        return values;
    }

    public void setValues(Iterable<?> values) {
        this.values.clear();
        for (Object value : values) {
            addValue(value);
        }
    }

    public DefaultTaskDependency add(Object... values) {
        for (Object value : values) {
            addValue(value);
        }
        return this;
    }

    private void addValue(Object dependency) {
        if (dependency == null) {
            throw new InvalidUserDataException("A dependency must not be empty");
        }
        this.values.add(dependency);
    }
}
