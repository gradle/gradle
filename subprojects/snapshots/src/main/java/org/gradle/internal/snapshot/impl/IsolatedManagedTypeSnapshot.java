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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.instantiation.Managed;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;

public class IsolatedManagedTypeSnapshot extends AbstractManagedTypeSnapshot<Isolatable<?>> implements Isolatable<Object> {
    private final Managed.Factory factory;
    private final Class<?> targetType;

    public IsolatedManagedTypeSnapshot(Class<?> targetType, Managed.Factory factory, Isolatable<?> state) {
        super(state);
        this.targetType = targetType;
        this.factory = factory;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return new ManagedTypeSnapshot(targetType.getName(), state.asSnapshot());
    }

    @Override
    public Object isolate() {
        return factory.fromState(targetType, state.isolate());
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isAssignableFrom(targetType)) {
            return type.cast(isolate());
        }
        if (targetType.getName().equals(type.getName())) {
            return type.cast(factory.fromState(type, state.isolate()));
        }
        return null;
    }
}
