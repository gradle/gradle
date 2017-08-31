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
import org.gradle.internal.Cast;
import org.gradle.internal.MutableActionSet;

public class BroadcastingCollectionEventRegister<T> implements CollectionEventRegister<T> {
    private final MutableActionSet<T> addActions = new MutableActionSet<T>();
    private final MutableActionSet<T> removeActions = new MutableActionSet<T>();

    public Action<T> getAddAction() {
        return addActions;
    }

    public Action<T> getRemoveAction() {
        return removeActions;
    }

    public Action<? super T> registerAddAction(Action<? super T> addAction) {
        addActions.add(addAction);
        return addAction;
    }

    public Action<? super T> registerRemoveAction(Action<? super T> removeAction) {
        removeActions.add(removeAction);
        return removeAction;
    }

    public <S extends T> CollectionEventRegister<S> filtered(CollectionFilter<S> filter) {
        CollectionEventRegister<S> cast = Cast.uncheckedCast(this);
        return new FilteringCollectionEventRegister<S>(filter, cast);
    }
}
