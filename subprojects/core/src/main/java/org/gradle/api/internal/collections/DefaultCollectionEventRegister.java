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
package org.gradle.api.internal.collections;

import org.gradle.api.Action;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.internal.ImmutableActionSet;

import java.util.HashSet;
import java.util.Set;

public class DefaultCollectionEventRegister<T> implements CollectionEventRegister<T> {

    private final Class<? extends T> baseType;
    private final CollectionCallbackActionDecorator decorator;

    private ImmutableActionSet<T> addActions = ImmutableActionSet.empty();
    private ImmutableActionSet<T> removeActions = ImmutableActionSet.empty();

    private boolean baseTypeSubscribed;
    private Set<Class<?>> subscribedTypes;

    public DefaultCollectionEventRegister(Class<? extends T> baseType, CollectionCallbackActionDecorator decorator) {
        this.baseType = baseType;
        this.decorator = decorator;
    }

    @Override
    public boolean isSubscribed(Class<?> type) {
        if (baseTypeSubscribed) {
            return true;
        }
        if (subscribedTypes != null) {
            if (type == null) {
                return true;
            }
            for (Class<?> subscribedType : subscribedTypes) {
                if (subscribedType.isAssignableFrom(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public CollectionCallbackActionDecorator getDecorator() {
        return decorator;
    }

    @Override
    public ImmutableActionSet<T> getAddActions() {
        return addActions;
    }

    @Override
    public void fireObjectAdded(T element) {
        addActions.execute(element);
    }

    @Override
    public void fireObjectRemoved(T element) {
        removeActions.execute(element);
    }

    @Override
    public Action<? super T> registerEagerAddAction(Class<? extends T> type, Action<? super T> addAction) {
        return registerEagerAddDecoratedAction(type, decorate(addAction));
    }

    @Override
    public Action<? super T> registerLazyAddAction(Action<? super T> addAction) {
        return registerLazyAddDecoratedAction(decorate(addAction));
    }

    @Override
    public void registerRemoveAction(Class<? extends T> type, Action<? super T> removeAction) {
        registerRemoveDecoratedAction(decorate(removeAction));
    }

    private Action<? super T> registerEagerAddDecoratedAction(Class<? extends T> type, Action<? super T> decorated) {
        subscribe(type);
        return registerLazyAddDecoratedAction(decorated);
    }

    private Action<? super T> registerLazyAddDecoratedAction(Action<? super T> decorated) {
        addActions = addActions.add(decorated);
        return decorated;
    }

    private void registerRemoveDecoratedAction(Action<? super T> decorated) {
        removeActions = removeActions.add(decorated);
    }

    private Action<? super T> decorate(Action<? super T> action) {
        return decorator.decorate(action);
    }

    private <S extends T> Action<? super T> decorate(Action<? super S> action, CollectionFilter<S> filter) {
        return filter.filtered(decorator.decorate(action));
    }

    @Override
    public <S extends T> CollectionEventRegister<S> filtered(CollectionFilter<S> filter) {
        return new FilteredEventRegister<S>(filter);
    }

    private void subscribe(Class<? extends T> type) {
        if (baseTypeSubscribed) {
            return;
        }
        if (type.equals(baseType)) {
            baseTypeSubscribed = true;
            subscribedTypes = null;
        } else {
            if (subscribedTypes == null) {
                subscribedTypes = new HashSet<Class<?>>();
            }
            subscribedTypes.add(type);
        }
    }

    private class FilteredEventRegister<S extends T> implements CollectionEventRegister<S> {
        private final CollectionFilter<S> filter;

        FilteredEventRegister(CollectionFilter<S> filter) {
            this.filter = filter;
        }

        @Override
        public CollectionCallbackActionDecorator getDecorator() {
            return decorator;
        }

        @Override
        public ImmutableActionSet<S> getAddActions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fireObjectAdded(S element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fireObjectRemoved(S element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSubscribed(Class<?> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Action<? super S> registerEagerAddAction(Class<? extends S> type, Action<? super S> addAction) {
            return registerEagerAddDecoratedAction(type, decorate(addAction, filter));
        }

        @Override
        public Action<? super S> registerLazyAddAction(Action<? super S> addAction) {
            return registerLazyAddDecoratedAction(decorate(addAction, filter));
        }

        @Override
        public void registerRemoveAction(Class<? extends S> type, Action<? super S> removeAction) {
            registerRemoveDecoratedAction(decorate(removeAction, filter));
        }

        @Override
        public <N extends S> CollectionEventRegister<N> filtered(CollectionFilter<N> filter) {
            return new FilteredEventRegister<N>(filter);
        }
    }

}
