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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Buildable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.ValueSupplier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Cast;
import org.gradle.internal.typeconversion.UnsupportedNotationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.google.common.collect.Iterables.toArray;
import static org.gradle.util.internal.GUtil.uncheckedCall;

/**
 * A task dependency which can have both mutable and immutable dependency values.
 *
 * If dependencies are known up-front, it is much more efficient to pass
 * them as immutable values to the {@link DefaultTaskDependency#DefaultTaskDependency(TaskResolver, ImmutableSet, TaskDependencyUsageTracker)}
 * constructor than to use the {@link #add(Object...)} method, as the former will
 * require less memory to store them.
 */
public class DefaultTaskDependency extends AbstractTaskDependency {
    private final ImmutableSet<Object> immutableValues;
    private Set<Object> mutableValues;
    private final TaskResolver resolver;

    public DefaultTaskDependency() {
        this(null, null);
    }

    public DefaultTaskDependency(
        @Nullable TaskResolver resolver,
        @Nullable TaskDependencyUsageTracker taskDependencyUsageTracker
    ) {
        this(resolver, ImmutableSet.of(), taskDependencyUsageTracker);
    }

    public DefaultTaskDependency(
        @Nullable TaskResolver resolver,
        ImmutableSet<Object> immutableValues,
        @Nullable TaskDependencyUsageTracker taskDependencyUsageTracker
    ) {
        super(taskDependencyUsageTracker);
        this.resolver = resolver;
        this.immutableValues = immutableValues;
    }

    @Override
    public void visitDependencies(final TaskDependencyResolveContext context) {
        Set<Object> mutableValues = getMutableValues();
        if (mutableValues.isEmpty() && immutableValues.isEmpty()) {
            return;
        }
        final Deque<Object> queue = new ArrayDeque<Object>(mutableValues.size() + immutableValues.size());
        queue.addAll(immutableValues);
        queue.addAll(mutableValues);
        while (!queue.isEmpty()) {
            Object dependency = queue.removeFirst();
            if (dependency instanceof Buildable) {
                context.add(dependency);
            } else if (dependency instanceof Task) {
                context.add(dependency);
            } else if (dependency instanceof TaskDependency) {
                context.add(dependency);
            } else if (dependency instanceof ProviderInternal) {
                // When a Provider is used as a task dependency (rather than as a task input), need to unpack the value
                ProviderInternal<?> provider = (ProviderInternal<?>) dependency;
                ValueSupplier.ValueProducer producer = provider.getProducer();
                if (producer.isKnown()) {
                    producer.visitProducerTasks(context);
                } else {
                    // The provider does not know how to produce the value, so use the value instead
                    queue.addFirst(provider.get());
                }
            } else if (dependency instanceof TaskDependencyContainer) {
                ((TaskDependencyContainer) dependency).visitDependencies(context);
            } else if (dependency instanceof Closure) {
                Closure closure = (Closure) dependency;
                Object closureResult = closure.call(context.getTask());
                if (closureResult != null) {
                    queue.addFirst(closureResult);
                }
            } else if (dependency instanceof List) {
                List<?> list = (List) dependency;
                if (list instanceof RandomAccess) {
                    for (int i = list.size() - 1; i >= 0; i--) {
                        queue.addFirst(list.get(i));
                    }
                } else {
                    ListIterator<?> iterator = list.listIterator(list.size());
                    while (iterator.hasPrevious()) {
                        Object item = iterator.previous();
                        queue.addFirst(item);
                    }
                }
            } else if (dependency instanceof Iterable && !(dependency instanceof Path)) {
                // Path is Iterable, but we don't want to unpack it
                Iterable<?> iterable = Cast.uncheckedNonnullCast(dependency);
                addAllFirst(queue, toArray(iterable, Object.class));
            } else if (dependency instanceof Map) {
                Map<?, ?> map = Cast.uncheckedNonnullCast(dependency);
                addAllFirst(queue, map.values().toArray());
            } else if (dependency instanceof Object[]) {
                Object[] array = (Object[]) dependency;
                addAllFirst(queue, array);
            } else if (dependency instanceof Callable) {
                Callable<?> callable = Cast.uncheckedNonnullCast(dependency);
                Object callableResult = uncheckedCall(Cast.uncheckedNonnullCast(callable));
                if (callableResult != null) {
                    queue.addFirst(callableResult);
                }
            } else if (resolver != null && dependency instanceof CharSequence) {
                context.add(resolver.resolveTask(dependency.toString()));
            } else if (dependency instanceof VisitBehavior) {
                ((VisitBehavior) dependency).onVisit.accept(context);
            } else {
                List<String> formats = new ArrayList<String>();
                if (resolver != null) {
                    formats.add("A String or CharSequence task name or path");
                }
                formats.add("A Task instance");
                formats.add("A TaskReference instance");
                formats.add("A Buildable instance");
                formats.add("A TaskDependency instance");
                formats.add("A Provider that represents a task output");
                formats.add("A Provider instance that returns any of these types");
                formats.add("A Closure instance that returns any of these types");
                formats.add("A Callable instance that returns any of these types");
                formats.add("An Iterable, Collection, Map or array instance that contains any of these types");
                throw new UnsupportedNotationException(dependency, String.format("Cannot convert %s to a task.", dependency), null, formats);
            }
        }
    }

    private static void addAllFirst(Deque<Object> queue, Object[] items) {
        for (int i = items.length - 1; i >= 0; i--) {
            queue.addFirst(items[i]);
        }
    }

    public Set<Object> getMutableValues() {
        if (mutableValues == null) {
            mutableValues = new TaskDependencySet();
        }
        return mutableValues;
    }

    public void setValues(Iterable<?> values) {
        getMutableValues().clear();
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
        getMutableValues().add(dependency);
    }

    static class VisitBehavior {
        @Nonnull
        public final Consumer<? super TaskDependencyResolveContext> onVisit;

        public VisitBehavior(Consumer<? super TaskDependencyResolveContext> onVisit) {
            this.onVisit = onVisit;
        }
    }

    private static class TaskDependencySet implements Set<Object> {
        private final Set<Object> delegate = Sets.newHashSet();
        private final static String REMOVE_ERROR = "Removing a task dependency from a task instance is not supported.";

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public Iterator<Object> iterator() {
            return delegate.iterator();
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean add(Object o) {
            return delegate.add(o);
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException(REMOVE_ERROR);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<?> c) {
            return delegate.addAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException(REMOVE_ERROR);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException(REMOVE_ERROR);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean equals(Object o) {
            return delegate.equals(o);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }
}
