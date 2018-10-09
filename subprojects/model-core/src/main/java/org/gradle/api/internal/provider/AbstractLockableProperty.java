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

import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;

public abstract class AbstractLockableProperty<T> extends AbstractMinimalProvider<T> implements PropertyInternal<T> {
    private PropertyInternal<T> delegate;
    private boolean locked;
    private T value;
    private Class<T> type;
    private String realizedToString;

    protected AbstractLockableProperty(PropertyInternal<T> delegate) {
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

    @Override
    public T get() {
        if (locked) {
            if (value == null) {
                throw new IllegalStateException(Providers.NULL_VALUE);
            }
            return value;
        } else {
            return delegate.get();
        }
    }

    @Override
    public boolean isPresent() {
        return locked ? value != null : delegate.isPresent();
    }

    @Nullable
    @Override
    public T getOrNull() {
        return locked ? value : delegate.getOrNull();
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return delegate.maybeVisitBuildDependencies(context);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        delegate.visitDependencies(context);
    }

    public void finalizeValue() {
        locked = true;
        T currentValue = delegate.getOrNull();
        value = currentValue == null ? null : immutableCopy(currentValue);
        type = delegate.getType();
        realizedToString = delegate.toString();
        delegate = null;
    }

    protected abstract T immutableCopy(T value);

    protected void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("This property is locked and cannot be changed.");
        }
    }

    @Override
    public String toString() {
        if (locked) {
            return String.format("locked(%s, %s)", realizedToString, value);
        }
        return String.format("unlocked(%s)", delegate);
    }
}
