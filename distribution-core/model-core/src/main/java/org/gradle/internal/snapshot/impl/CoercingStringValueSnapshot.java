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

import org.gradle.api.Named;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

public class CoercingStringValueSnapshot extends StringValueSnapshot {
    private final NamedObjectInstantiator instantiator;

    public CoercingStringValueSnapshot(String value, NamedObjectInstantiator instantiator) {
        super(value);
        this.instantiator = instantiator;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isInstance(getValue())) {
            return type.cast(this);
        }
        if (type.isEnum()) {
            return type.cast(Enum.valueOf(Cast.uncheckedNonnullCast(type.asSubclass(Enum.class)), getValue()));
        }
        if (Named.class.isAssignableFrom(type)) {
            return type.cast(instantiator.named(type.asSubclass(Named.class), getValue()));
        }
        if (Integer.class.equals(type)) {
            return type.cast(Integer.valueOf(getValue()));
        }
        return null;
    }
}
