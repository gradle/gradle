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

import javax.annotation.Nullable;

public abstract class AbstractLockableProperty<T> extends AbstractProvider<T> implements PropertyInternal<T> {
    private PropertyInternal<T> delegate;
    private boolean locked;
    private T value;
    private Class<T> type;

    public AbstractLockableProperty(PropertyInternal<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setFromAnyValue(Object object) {
        assertNotLocked();
        delegate.setFromAnyValue(object);
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return locked ? type : delegate.getType();
    }

    @Nullable
    @Override
    public T getOrNull() {
        return locked ? value : delegate.getOrNull();
    }

    public void lockNow() {
        locked = true;
        T currentValue = delegate.getOrNull();
        value = currentValue == null ? null : immutableCopy(currentValue);
        type = delegate.getType();
        delegate = null;
    }

    protected abstract T immutableCopy(T value);

    protected void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("This property is locked and cannot be changed.");
        }
    }
}
