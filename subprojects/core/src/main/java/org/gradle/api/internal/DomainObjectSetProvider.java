/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.ChangingValue;
import org.gradle.api.internal.provider.ChangingValueHandler;
import org.gradle.api.internal.provider.CollectionProviderInternal;

import java.util.Set;

public class DomainObjectSetProvider<T> extends AbstractMinimalProvider<Set<T>> implements CollectionProviderInternal<T, Set<T>>, ChangingValue<Set<T>> {

    private Set<T> previous = null;

    private final DomainObjectSet<T> collection;
    private final ChangingValueHandler<Set<T>> changingValue = new ChangingValueHandler<>();

    public DomainObjectSetProvider(DomainObjectSet<T> collection) {
        this.collection = collection;
    }

    @Override
    protected Value<? extends Set<T>> calculateOwnValue(ValueConsumer consumer) {
        if (previous == null) {
            subscribe();
        }
        previous = ImmutableSet.copyOf(collection);
        return Value.of(previous);
    }

    private void subscribe() {
        // TODO: It's pretty lame that we need to add eager actions to determine if the backing collection is modified.
        // It would be nice if we could say collection.getStore().whenModified(() -> changingValue.handle(previous));
        collection.whenObjectAdded(obj -> {
            changingValue.handle(this.previous);
        });
        collection.whenObjectRemoved(obj -> {
            changingValue.handle(this.previous);
        });
    }

    @Override
    public void onValueChange(Action<Set<T>> action) {
        changingValue.onValueChange(action);
    }

    @Override
    public Class<? extends T> getElementType() {
        return ((DefaultDomainObjectSet<T>) collection).getType();
    }

    @Override
    public int size() {
        return collection.size();
    }

    @Override
    public Class<Set<T>> getType() {
        return null;
    }
}
