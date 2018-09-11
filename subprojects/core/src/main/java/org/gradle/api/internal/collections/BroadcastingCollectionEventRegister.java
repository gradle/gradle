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
import org.gradle.internal.ImmutableActionSet;

import java.util.HashSet;
import java.util.Set;

public class BroadcastingCollectionEventRegister<T> implements CollectionEventRegister<T> {
    private ImmutableActionSet<T> addActions = ImmutableActionSet.empty();
    private ImmutableActionSet<T> removeActions = ImmutableActionSet.empty();
    private final Class<? extends T> baseType;
    private boolean baseTypeSubscribed;
    private Set<Class<?>> subscribedTypes;

    public BroadcastingCollectionEventRegister(Class<? extends T> baseType) {
        this.baseType = baseType;
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
    public void registerEagerAddAction(Class<? extends T> type, Action<? super T> addAction) {
        subscribe(type);
        addActions = addActions.add(addAction);
    }

    @Override
    public void registerLazyAddAction(Action<? super T> addAction) {
        addActions = addActions.add(addAction);
    }

    @Override
    public void registerRemoveAction(Class<? extends T> type, Action<? super T> removeAction) {
        removeActions = removeActions.add(removeAction);
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
}
