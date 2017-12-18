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

package org.gradle.api.internal.provider;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;

import javax.annotation.Nullable;
import java.util.List;

public class LockableListProperty<T> extends AbstractProvider<List<T>> implements ListProperty<T>, PropertyInternal<List<T>> {
    private ListProperty<T> delegate;
    private boolean locked;
    private List<T> value;

    public LockableListProperty(ListProperty<T> delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public Class<List<T>> getType() {
        return null;
    }

    public void lockNow() {
        locked = true;
        value = delegate.getOrNull();
        delegate = null;
    }

    private void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("This property is locked and cannot be changed.");
        }
    }

    @Override
    public void setFromAnyValue(Object object) {
        assertNotLocked();
        ((PropertyInternal<?>) delegate).setFromAnyValue(object);
    }

    @Override
    public void add(T element) {
        assertNotLocked();
        delegate.add(element);
    }

    @Override
    public void add(Provider<? extends T> provider) {
        assertNotLocked();
        delegate.add(provider);
    }

    @Override
    public void addAll(Provider<? extends Iterable<T>> provider) {
        assertNotLocked();
        delegate.addAll(provider);
    }

    @Override
    public void set(@Nullable List<T> value) {
        assertNotLocked();
        delegate.set(value);
    }

    @Override
    public void set(Provider<? extends List<T>> provider) {
        assertNotLocked();
        delegate.set(provider);
    }

    @Override
    @Nullable
    @Internal
    public List<T> getOrNull() {
        return locked ? value : delegate.getOrNull();
    }
}
