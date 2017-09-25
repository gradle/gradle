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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.Named;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

public class IsolatedManagedNamedTypeSnapshot extends ManagedNamedTypeSnapshot implements Isolatable<Named> {
    private final Named value;
    private final NamedObjectInstantiator instantiator;

    public IsolatedManagedNamedTypeSnapshot(Named value, NamedObjectInstantiator instantiator) {
        super(value);
        this.value = value;
        this.instantiator = instantiator;
    }

    @Override
    public Named isolate() {
        return value;
    }

    @Nullable
    @Override
    public <S> Isolatable<S> coerce(Class<S> type) {
        if (type.isAssignableFrom(value.getClass())) {
            return Cast.uncheckedCast(this);
        }
        if (!Named.class.isAssignableFrom(type)) {
            return null;
        }
        for (Class<?> interfaceType : value.getClass().getInterfaces()) {
            if (interfaceType.getName().equals(type.getName())) {
                return Cast.uncheckedCast(new IsolatedManagedNamedTypeSnapshot(instantiator.named((Class<? extends Named>) type, value.getName()), instantiator));
            }
        }
        return null;
    }
}
