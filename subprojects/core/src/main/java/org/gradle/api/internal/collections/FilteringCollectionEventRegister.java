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
import org.gradle.api.specs.Specs;
import org.gradle.internal.Actions;

class FilteringCollectionEventRegister<T> implements CollectionEventRegister<T> {
    private final CollectionFilter<? super T> filter;
    private final CollectionEventRegister<T> delegate;

    public FilteringCollectionEventRegister(CollectionFilter<? super T> filter, CollectionEventRegister<T> delegate) {
        this.filter = filter;
        this.delegate = delegate;
    }

    @Override
    public Action<T> getAddAction() {
        return delegate.getAddAction();
    }

    @Override
    public Action<T> getRemoveAction() {
        return delegate.getRemoveAction();
    }

    public Action<? super T> registerAddAction(Action<? super T> addAction) {
        return delegate.registerAddAction(Actions.<T>filter(addAction, filter));
    }

    public Action<? super T> registerRemoveAction(Action<? super T> removeAction) {
        return delegate.registerRemoveAction(Actions.<T>filter(removeAction, filter));
    }

    public <S extends T> CollectionEventRegister<S> filtered(CollectionFilter<S> filter) {
        return delegate.filtered(new CollectionFilter<S>(filter.getType(), Specs.intersect(filter, this.filter)));
    }
}
