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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;

public class LockableProperty<T> extends AbstractLockableProperty<T> implements Property<T> {
    private Property<T> delegate;

    public LockableProperty(Property<T> delegate) {
        super((PropertyInternal<T>)delegate);
        this.delegate = delegate;
    }

    @Override
    public void set(@Nullable T value) {
        assertNotLocked();
        delegate.set(value);
    }

    @Override
    public void set(Provider<? extends T> provider) {
        assertNotLocked();
        delegate.set(provider);
    }

    @Override
    public void lockNow() {
        super.lockNow();
        delegate = null;
    }

    @Override
    protected T immutableCopy(T value) {
        return value;
    }
}
