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

import org.gradle.api.internal.provider.DefaultMapProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.internal.isolation.Isolatable;

import javax.annotation.Nullable;
import java.util.Map;

class IsolatedMapProperty extends IsolatedProvider {
    private final Class<?> keyType;
    private final Class<?> valueType;

    public IsolatedMapProperty(Class<?> keyType, Class<?> valueType, Isolatable<?> value) {
        super(value);
        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Nullable
    @Override
    public MapProperty<Object, Object> isolate() {
        DefaultMapProperty<Object, Object> property = new DefaultMapProperty<>((Class<Object>) keyType, (Class<Object>) valueType);
        property.set((Map<?, ?>) value.isolate());
        return property;
    }
}
