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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.internal.ImmutableActionSet;

import javax.annotation.Nullable;

class BuildOperationActionDecoratingCollectionEventRegistrar<T> implements CollectionEventRegister<T> {
    private CollectionEventRegister<T> delegate;
    private DomainObjectCollectionCallbackDecorator decorator;

    public BuildOperationActionDecoratingCollectionEventRegistrar(DomainObjectCollectionCallbackDecorator decorator, CollectionEventRegister<T> delegate) {
        this.decorator = decorator;
        this.delegate = delegate;
    }

    @Override
    public boolean isSubscribed(@Nullable Class<?> type) {
        return delegate.isSubscribed(type);
    }

    @Override
    public ImmutableActionSet<T> getAddActions() {
        return delegate.getAddActions();
    }

    @Override
    public void fireObjectAdded(T element) {
        delegate.fireObjectAdded(element);
    }

    @Override
    public void fireObjectRemoved(T element) {
        delegate.fireObjectRemoved(element);
    }

    @Override
    public void registerEagerAddAction(Class<? extends T> type, Action<? super T> addAction) {
        if (decorator == null) {
            delegate.registerEagerAddAction(type, addAction);
        } else {
            delegate.registerEagerAddAction(type, decorator.decorate(addAction));
        }
    }

    @Override
    public void registerLazyAddAction(Action<? super T> addAction) {
        delegate.registerLazyAddAction(addAction);
    }

    @Override
    public void registerRemoveAction(Class<? extends T> type, Action<? super T> removeAction) {
        delegate.registerRemoveAction(type, removeAction);
    }


}
