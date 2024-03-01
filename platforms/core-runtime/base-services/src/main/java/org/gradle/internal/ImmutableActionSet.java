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
 * Implements {@link InternalListener} as components themselves should be decorated if appropriate.
 *
 * @param <T> the type of the subject of the action
 */
public abstract class ImmutableActionSet<T> implements Action<T>, InternalListener {
    private static final int FEW_VALUES = 5;
    private static final ImmutableActionSet<Object> EMPTY = new EmptySet<Object>();

    /**
     * Creates an empty action set.
     */
    public static <T> ImmutableActionSet<T> empty() {
        return Cast.uncheckedNonnullCast(EMPTY);
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
        return fromActions(set);
    }

    private static <T> void unpackAction(Action<? super T> action, ImmutableSet.Builder<Action<? super T>> builder) {
        if (action instanceof ImmutableActionSet) {
            ImmutableActionSet<T> immutableSet = Cast.uncheckedNonnullCast(action);
            immutableSet.unpackInto(builder);
        } else {
            builder.add(action);
        }
    }

    protected abstract void unpackInto(ImmutableSet.Builder<Action<? super T>> builder);

    /**
     * Creates a new set that runs the actions of this set plus the given action.
     */
    public ImmutableActionSet<T> add(Action<? super T> action) {
        if (action == Actions.DO_NOTHING || action instanceof EmptySet || action == this) {
            return this;
        }
        if (action instanceof SingletonSet) {
            SingletonSet<T> singletonSet = Cast.uncheckedNonnullCast(action);
            return addOne(singletonSet.singleAction);
        }
        if (action instanceof SetWithFewActions) {
            SetWithFewActions<T> compositeSet = Cast.uncheckedNonnullCast(action);
            return addAll(compositeSet);
        }
        if (action instanceof SetWithManyActions) {
            SetWithManyActions<T> compositeSet = Cast.uncheckedNonnullCast(action);
            return addAll(compositeSet);
        }
        return addOne(action);
    }

    private static <T> ImmutableActionSet<T> plus(ImmutableActionSet<T> one, ImmutableActionSet<T> two) {
        ImmutableSet.Builder<Action<? super T>> builder = ImmutableSet.builder();
        one.unpackInto(builder);
        two.unpackInto(builder);
        ImmutableSet<Action<? super T>> set = builder.build();
        return fromActions(set);
    }

    private static <T> ImmutableActionSet<T> plus(ImmutableActionSet<T> one, Action<? super T> two) {
        ImmutableSet.Builder<Action<? super T>> builder = ImmutableSet.builder();
        one.unpackInto(builder);
        builder.add(two);
        ImmutableSet<Action<? super T>> set = builder.build();
        return fromActions(set);
    }

    private static <T> ImmutableActionSet<T> fromActions(ImmutableSet<Action<? super T>> set) {
        if (set.isEmpty()) {
            return empty();
        }
        if (set.size() == 1) {
            return new SingletonSet<T>(set.iterator().next());
        }
        if (set.size() <= FEW_VALUES) {
            return new SetWithFewActions<T>(set);
        }
        return new SetWithManyActions<T>(set);
    }

    /**
     * Creates a new set that includes the actions from this set plus the actions from the given set.
     */
    public ImmutableActionSet<T> mergeFrom(ImmutableActionSet<? super T> sibling) {
        if (sibling == this) {
            return this;
        }
        if (sibling.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return Cast.uncheckedNonnullCast(sibling);
        }
        return add(sibling);
    }

    /**
     * Does this set do anything?
     */
    public abstract boolean isEmpty();

    abstract ImmutableActionSet<T> addAll(SetWithFewActions<T> source);

    abstract ImmutableActionSet<T> addAll(SetWithManyActions<T> source);

    abstract ImmutableActionSet<T> addOne(Action<? super T> action);

    private static class EmptySet<T> extends ImmutableActionSet<T> {
        @Override
        ImmutableActionSet<T> addOne(Action<? super T> action) {
            return new SingletonSet<T>(action);
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithManyActions<T> source) {
            return source;
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithFewActions<T> source) {
            return source;
        }

        @Override
        protected void unpackInto(ImmutableSet.Builder<Action<? super T>> builder) {
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
        ImmutableActionSet<T> addOne(Action<? super T> action) {
            if (action.equals(singleAction)) {
                return this;
            }
            return new SetWithFewActions<T>(Cast.<Action<? super T>[]>uncheckedNonnullCast(new Action<?>[]{singleAction, action}));
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithFewActions<T> source) {
            if (singleAction.equals(source.actions[0])) {
                // Already at the front. If not at the front, need to recreate
                return source;
            }
            if (source.actions.length < FEW_VALUES && !source.contains(singleAction)) {
                // Adding a small set with no duplicates
                Action<? super T>[] newActions = Cast.uncheckedNonnullCast(new Action<?>[source.actions.length + 1]);
                newActions[0] = singleAction;
                System.arraycopy(source.actions, 0, newActions, 1, source.actions.length);
                return new SetWithFewActions<T>(newActions);
            }
            return plus(this, source);
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithManyActions<T> source) {
            return plus(this, source);
        }

        @Override
        protected void unpackInto(ImmutableSet.Builder<Action<? super T>> builder) {
            builder.add(singleAction);
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

    private static class SetWithFewActions<T> extends ImmutableActionSet<T> {
        private final Action<? super T>[] actions;

        SetWithFewActions(ImmutableSet<Action<? super T>> set) {
            actions = Cast.uncheckedNonnullCast(set.toArray(new Action<?>[set.size()]));
        }

        SetWithFewActions(Action<? super T>[] actions) {
            this.actions = actions;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        ImmutableActionSet<T> addOne(Action<? super T> action) {
            if (contains(action)) {
                // Duplicate, ignore
                return this;
            }
            if (actions.length < FEW_VALUES) {
                // Adding an action that is not a duplicate
                Action<? super T>[] newActions = Cast.uncheckedNonnullCast(new Action<?>[actions.length + 1]);
                System.arraycopy(actions, 0, newActions, 0, actions.length);
                newActions[actions.length] = action;
                return new SetWithFewActions<T>(newActions);
            }

            return plus(this, action);
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithFewActions<T> source) {
            return plus(this, source);
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithManyActions<T> source) {
            return plus(this, source);
        }

        @Override
        protected void unpackInto(ImmutableSet.Builder<Action<? super T>> builder) {
            builder.add(actions);
        }

        @Override
        public void execute(T t) {
            for (Action<? super T> action : actions) {
                action.execute(t);
            }
        }

        public boolean contains(Action<? super T> action) {
            for (Action<? super T> current : actions) {
                if (current.equals(action)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class SetWithManyActions<T> extends ImmutableActionSet<T> {
        private final ImmutableSet<Action<? super T>> multipleActions;

        SetWithManyActions(ImmutableSet<Action<? super T>> multipleActions) {
            this.multipleActions = multipleActions;
        }

        @Override
        ImmutableActionSet<T> addOne(Action<? super T> action) {
            return plus(this, action);
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithManyActions<T> source) {
            return plus(this, source);
        }

        @Override
        ImmutableActionSet<T> addAll(SetWithFewActions<T> source) {
            return plus(this, source);
        }

        @Override
        protected void unpackInto(ImmutableSet.Builder<Action<? super T>> builder) {
            builder.addAll(multipleActions);
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
