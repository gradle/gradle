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

import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Collection;

public abstract class LockableCollectionProperty<T, C extends Collection<T>> extends AbstractLockableProperty<C> implements CollectionPropertyInternal<T, C> {
    private CollectionPropertyInternal<T, C> delegate;
    private boolean locked;
    private C value;

    public LockableCollectionProperty(CollectionPropertyInternal<T, C> delegate) {
        super(delegate);
        this.delegate = delegate;
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
    public void set(@Nullable Iterable<? extends T> value) {
        assertNotLocked();
        delegate.set(value);
    }

    @Override
    public void set(Provider<? extends Iterable<? extends T>> provider) {
        assertNotLocked();
        delegate.set(provider);
    }

    @Override
    public void lockNow() {
        super.lockNow();
        delegate = null;
    }
}
