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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.Transformer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Actions {

    /**
     * Creates an action implementation that simply does nothing.
     *
     * A new action instance is created each time.
     *
     * @return An action object with an empty implementation
     */
    public static Action<Object> doNothing() {
        return new NullAction();
    }

    private static class NullAction implements Action<Object>, Serializable {
        public void execute(Object t) {}
    }

    /**
     * Creates an action that will call each of the given actions in order.
     *
     * @param actions The actions to make a composite of.
     * @param <T> The type of the object that action is for
     * @return The composite action.
     */
    public static <T> Action<T> composite(Action<? super T>... actions) {
        final List<Action<? super T>> actionsCopy = new ArrayList<Action<? super T>>(actions.length);
        Collections.addAll(actionsCopy, actions);
        return new CompositeAction<T>(actionsCopy);
   }

    private static class CompositeAction<T> implements Action<T> {
        private final Iterable<Action<? super T>> actions;

        private CompositeAction(Iterable<Action<? super T>> actions) {
            this.actions = actions;
        }

        public void execute(T item) {
            for (Action<? super T> action : actions) {
                action.execute(item);
            }
        }
    }

    /**
     * Creates a new composite action, where the argument is first transformed.
     *
     * @param action The action.
     * @param transformer The transformer to transform the argument with
     * @param <T> The type the action is expecting (that the argument is transformed to)
     * @param <I> The type of the original argument
     * @return An action that transforms an object of type I to type O to give to the given action
     */
    public static <T, I> Action<I> transformBefore(final Action<? super T> action, final Transformer<? extends T, ? super I> transformer) {
        return new TransformingActionAdapter<T, I>(transformer, action);
    }

    private static class TransformingActionAdapter<T, I> implements Action<I> {
        private final Transformer<? extends T, ? super I> transformer;
        private final Action<? super T> action;

        private TransformingActionAdapter(Transformer<? extends T, ? super I> transformer, Action<? super T> action) {
            this.transformer = transformer;
            this.action = action;
        }

        public void execute(I thing) {
            T transformed = transformer.transform(thing);
            action.execute(transformed);
        }
    }

    /**
     * Adapts an action to a different type by casting the object before giving it to the action.
     *
     * @param actionType The type the action is expecting
     * @param action The action
     * @param <T> The type the action is expecting
     * @param <I> The type before casting
     * @return An action that casts the object to the given type before giving it to the given action
     */
    public static <T, I> Action<I> castBefore(final Class<T> actionType, final Action<? super T> action) {
        return transformBefore(action, new CastingTransformer<T, I>(actionType));
    }

    /**
     * Wraps the given runnable in an {@link Action}, where the execute implementation runs the runnable ignoring the argument.
     *
     * If the given runnable is {@code null}, the action returned is effectively a noop.
     *
     * @param runnable The runnable to run for the action execution.
     * @return An action that runs the given runnable, ignoring the argument.
     */
    public static Action<Object> toAction(Runnable runnable) {
        return runnable == null ? doNothing() : new RunnableActionAdapter(runnable);
    }

    private static class RunnableActionAdapter implements Action<Object> {
        private final Runnable runnable;

        private RunnableActionAdapter(Runnable runnable) {
            this.runnable = runnable;
        }

        public void execute(Object o) {
            runnable.run();
        }

        @Override
        public String toString() {
            return String.format("RunnableActionAdapter{runnable=%s}", runnable);
        }
    }

}
