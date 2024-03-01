/*
 * Copyright 2011 the original author or authors.
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

import javax.annotation.Nullable;

public interface CollectionEventRegister<T> extends EventSubscriptionVerifier<T> {

    @Override
    boolean isSubscribed(@Nullable Class<? extends T> type);

    /**
     * Returns a snapshot of the <em>current</em> set of actions to run when an element is added.
     */
    ImmutableActionSet<T> getAddActions();

    void fireObjectAdded(T element);

    void fireObjectRemoved(T element);

    Action<? super T> registerEagerAddAction(Class<? extends T> type, Action<? super T> addAction);

    Action<? super T> registerLazyAddAction(Action<? super T> addAction);

    void registerRemoveAction(Class<? extends T> type, Action<? super T> removeAction);

    <S extends T> CollectionEventRegister<S> filtered(CollectionFilter<S> filter);

    // TODO: Migrate this away from here
    CollectionCallbackActionDecorator getDecorator();
}
