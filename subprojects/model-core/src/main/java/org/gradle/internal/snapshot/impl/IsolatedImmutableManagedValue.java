/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.hash.Hasher;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.state.Managed;
import org.gradle.internal.state.ManagedFactory;
import org.gradle.internal.state.ManagedFactoryRegistry;

import javax.annotation.Nullable;

public class IsolatedImmutableManagedValue extends AbstractIsolatableScalarValue<Managed> {
    private final ManagedFactoryRegistry managedFactoryRegistry;

    public IsolatedImmutableManagedValue(Managed managed, ManagedFactoryRegistry managedFactoryRegistry) {
        super(managed);
        this.managedFactoryRegistry = managedFactoryRegistry;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return new ImmutableManagedValueSnapshot(getValue().publicType().getName(), (String) getValue().unpackState());
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        asSnapshot().appendToHasher(hasher);
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isInstance(getValue())) {
            return type.cast(getValue());
        }
        ManagedFactory factory = managedFactoryRegistry.lookup(getValue().getFactoryId());
        if (factory == null) {
            return null;
        } else {
            return type.cast(factory.fromState(type, getValue().unpackState()));
        }
    }
}
