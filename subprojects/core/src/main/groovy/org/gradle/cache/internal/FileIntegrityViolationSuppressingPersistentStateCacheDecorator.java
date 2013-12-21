/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.cache.PersistentStateCache;

public class FileIntegrityViolationSuppressingPersistentStateCacheDecorator<T> implements PersistentStateCache<T> {

    private final PersistentStateCache<T> delegate;

    public FileIntegrityViolationSuppressingPersistentStateCacheDecorator(PersistentStateCache<T> delegate) {
        this.delegate = delegate;
    }

    public T get() {
        try {
            return delegate.get();
        } catch (FileIntegrityViolationException e) {
            return null;
        }
    }

    public void set(T newValue) {
        delegate.set(newValue);
    }

    public void update(final UpdateAction<T> updateAction) {
        try {
            delegate.update(updateAction);
        } catch (FileIntegrityViolationException e) {
            T newValue = updateAction.update(null);
            set(newValue);
        }
    }
}
