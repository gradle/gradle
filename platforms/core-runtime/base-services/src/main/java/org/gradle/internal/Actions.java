/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class Actions {

    static final Action<?> DO_NOTHING = new NullAction<Object>();

    /**
     * Creates an action implementation that simply does nothing.
     *
     * A new action instance is created each time.
     *
     * @return An action object with an empty implementation
     */
    @SuppressWarnings("unchecked")
    public static <T> Action<T> doNothing() {
        return (Action<T>) DO_NOTHING;
    }

    private static class NullAction<T> implements Action<T>, Serializable {
        @Override
        public void execute(T t) {
        }
    }

    /**
     * Creates an action that will call each of the given actions in order.
     *
     * @param actions The actions to make a composite of.
     * @param <T> The type of the object that action is for
     * @return The composite action.
     */
    public static <T> Action<T> composite(Iterable<? extends Action<? super T>> actions) {
        ImmutableList.Builder<Action<? super T>> builder = ImmutableList.builder();
        for (Action<? super T> action : actions) {
            if (doesSomething(action)) {
                builder.add(action);
            }
        }
        return composite(builder.build());
    }

    /**
     * Creates an action that will call each of the given actions in order.
     *
     * @param actions The actions to make a composite of.
     * @param <T> The type of the object that action is for
     * @return The composite action.
     */
    public static <T> Action<T> composite(List<? extends Action<? super T>> actions) {
        if (actions.isEmpty()) {
            return doNothing();
        }
        if (actions.size() == 1) {
            return Cast.uncheckedCast(actions.get(0));
        }
        return new CompositeAction<T>(actions);
    }

    /**
     * Creates an action that will call each of the given actions in order.
     *
     * @param actions The actions to make a composite of.
     * @param <T> The type of the object that action is for
     * @return The composite action.
     */
    @SafeVarargs
    public static <T> Action<T> composite(Action<? super T>... actions) {
        List<Action<? super T>> filtered = new ArrayList<Action<? super T>>(actions.length);
        for (Action<? super T> action : actions) {
            if (doesSomething(action)) {
                filtered.add(action);
            }
        }
        return composite(filtered);
    }

    private static class CompositeAction<T> implements Action<T> {
        private final List<? extends Action<? super T>> actions;

        private CompositeAction(List<? extends Action<? super T>> actions) {
            this.actions = actions;
        }

        @Override
        public void execute(T item) {
            for (Action<? super T> action : actions) {
                action.execute(item);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CompositeAction<?> that = (CompositeAction<?>) o;

            if (!actions.equals(that.actions)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return actions.hashCode();
        }
    }

    /**
     * Wraps the given runnable in an {@link Action}, where the execute implementation runs the runnable ignoring the argument.
     *
     * If the given runnable is {@code null}, the action returned is effectively a noop.
     *
     * @param runnable The runnable to run for the action execution.
     * @return An action that runs the given runnable, ignoring the argument.
     */
    public static <T> Action<T> toAction(@Nullable Runnable runnable) {
        //TODO SF this method accepts Closure instance as parameter but does not work correctly for it
        if (runnable == null) {
            return Actions.doNothing();
        } else {
            return new RunnableActionAdapter<T>(runnable);
        }
    }

    private static class RunnableActionAdapter<T> implements Action<T> {
        private final Runnable runnable;

        private RunnableActionAdapter(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void execute(T t) {
            runnable.run();
        }

        @Override
        public String toString() {
            return "RunnableActionAdapter{runnable=" + runnable + "}";
        }
    }

    /**
     * Creates a new action that only forwards arguments on to the given filter if they are satisfied by the given spec.
     *
     * @param action The action to delegate filtered items to
     * @param filter The spec to use to filter items by
     * @param <T> The type of item the action expects
     * @return A new action that only forwards arguments on to the given filter is they are satisfied by the given spec.
     */
    public static <T> Action<T> filter(Action<? super T> action, Spec<? super T> filter) {
        return new FilteredAction<T>(action, filter);
    }

    private static class FilteredAction<T> implements Action<T> {
        private final Spec<? super T> filter;
        private final Action<? super T> action;

        public FilteredAction(Action<? super T> action, Spec<? super T> filter) {
            this.filter = filter;
            this.action = action;
        }

        @Override
        public void execute(T t) {
            if (filter.isSatisfiedBy(t)) {
                action.execute(t);
            }
        }
    }

    public static <T> T with(T instance, Action<? super T> action) {
        action.execute(instance);
        return instance;
    }

    public static <T> Action<T> add(final Collection<? super T> collection) {
        return new Action<T>() {
            @Override
            public void execute(T t) {
                collection.add(t);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T> Action<T> set(Action<T>... actions) {
        return ImmutableActionSet.of(actions);
    }

    private static boolean doesSomething(Action<?> action) {
        return action != DO_NOTHING;
    }
}
