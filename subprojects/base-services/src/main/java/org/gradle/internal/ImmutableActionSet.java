/*
 * Copyright 2017 the original author or authors.
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


import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;

/**
 * An immutable composite {@link Action} implementation which has set semantics. Optimized for high execute to mutate ratio, and for a small number of actions.
 *
 * This set also INTENTIONALLY ignores {@link Actions#doNothing()} actions and empty sets as to avoid growing for something that would never do anything.
 *
 * Actions are executed in order of insertion. Duplicates are ignored. Execution stops on the first failure.
 *
 * @param <T> the type of the subject of the action
 */
public abstract class ImmutableActionSet<T> implements Action<T> {
    private static final ImmutableActionSet<Object> EMPTY = new EmptySet<Object>();

    /**
     * Creates an empty action set.
     */
    public static <T> ImmutableActionSet<T> empty() {
        return Cast.uncheckedCast(EMPTY);
    }

    /**
     * Creates an action set.
     */
    public static <T> ImmutableActionSet<T> of(Action<? super T>... actions) {
        if (actions.length == 0) {
            return empty();
        }

        ImmutableSet.Builder<Action<? super T>> builder = ImmutableSet.builder();
        for (Action<? super T> action : actions) {
            if (action == Actions.DO_NOTHING || (action instanceof EmptySet)) {
                continue;
            }
            unpackAction(action, builder);
        }
        ImmutableSet<Action<? super T>> set = builder.build();
        if (set.isEmpty()) {
            return empty();
        }
        if (set.size() == 1) {
            return new SingletonSet<T>(set.iterator().next());
        }
        return new CompositeSet<T>(set);
    }

    private static <T> void unpackAction(Action<? super T> action, ImmutableSet.Builder<Action<? super T>> builder) {
        if (action instanceof SingletonSet) {
            SingletonSet<T> singletonSet = (SingletonSet) action;
            builder.add(singletonSet.singleAction);
        } else if (action instanceof CompositeSet) {
            CompositeSet<T> compositeSet = (CompositeSet) action;
            builder.addAll(compositeSet.multipleActions);
        } else {
            builder.add(action);
        }
    }

    /**
     * Creates a new set that runs the actions of this set plus the given action.
     */
    public ImmutableActionSet<T> add(Action<? super T> action) {
        if (action == Actions.DO_NOTHING || action instanceof EmptySet || action == this) {
            return this;
        }
        if (action instanceof SingletonSet) {
            SingletonSet<T> singletonSet = (SingletonSet) action;
            return doAdd(singletonSet.singleAction);
        }
        if (action instanceof CompositeSet) {
            CompositeSet<T> compositeSet = (CompositeSet) action;
            return doAddAll(compositeSet);
        }
        return doAdd(action);
    }

    /**
     * Does this set do anything?
     */
    public abstract boolean isEmpty();

    abstract ImmutableActionSet<T> doAddAll(CompositeSet<T> source);

    abstract ImmutableActionSet<T> doAdd(Action<? super T> action);

    private static class EmptySet<T> extends ImmutableActionSet<T> {
        @Override
        ImmutableActionSet<T> doAdd(Action<? super T> action) {
            return new SingletonSet<T>(action);
        }

        @Override
        ImmutableActionSet<T> doAddAll(CompositeSet<T> source) {
            return source;
        }

        @Override
        public void execute(Object o) {
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }

    private static class SingletonSet<T> extends ImmutableActionSet<T> {
        private final Action<? super T> singleAction;

        SingletonSet(Action<? super T> singleAction) {
            this.singleAction = singleAction;
        }

        @Override
        ImmutableActionSet<T> doAdd(Action<? super T> action) {
            if (action.equals(singleAction)) {
                return this;
            }
            ImmutableSet<Action<? super T>> of = Cast.uncheckedCast(ImmutableSet.of(singleAction, action));
            return new CompositeSet<T>(of);
        }

        @Override
        ImmutableActionSet<T> doAddAll(CompositeSet<T> source) {
            if (source.multipleActions.contains(singleAction)) {
                return source;
            }
            ImmutableSet.Builder<Action<? super T>> builder = ImmutableSet.builder();
            builder.add(singleAction);
            builder.addAll(source.multipleActions);
            return new CompositeSet<T>(builder.build());
        }

        @Override
        public void execute(T t) {
            singleAction.execute(t);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private static class CompositeSet<T> extends ImmutableActionSet<T> {
        private final ImmutableSet<Action<? super T>> multipleActions;

        CompositeSet(ImmutableSet<Action<? super T>> multipleActions) {
            this.multipleActions = multipleActions;
        }

        @Override
        ImmutableActionSet<T> doAdd(Action<? super T> action) {
            if (multipleActions.contains(action)) {
                return this;
            }
            ImmutableSet.Builder<Action<? super T>> builder = ImmutableSet.builder();
            builder.addAll(multipleActions);
            builder.add(action);
            return new CompositeSet<T>(builder.build());
        }

        @Override
        ImmutableActionSet<T> doAddAll(CompositeSet<T> source) {
            if (multipleActions.containsAll(source.multipleActions)) {
                return this;
            }
            ImmutableSet.Builder<Action<? super T>> builder = ImmutableSet.builder();
            builder.addAll(multipleActions);
            builder.addAll(source.multipleActions);
            return new CompositeSet<T>(builder.build());
        }

        @Override
        public void execute(T t) {
            for (Action<? super T> action : multipleActions) {
                action.execute(t);
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
