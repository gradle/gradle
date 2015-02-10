/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.model.internal.type.ModelType;

import java.lang.ref.WeakReference;

public class ModelStructSchema<T> extends ModelSchema<T> {
    private final WeakReference<Class<? extends T>> managedImpl;
    private final ImmutableSortedMap<String, ModelProperty<?>> properties;

    public ModelStructSchema(ModelType<T> type, Iterable<ModelProperty<?>> properties, Class<? extends T> managedImpl) {
        super(type, Kind.STRUCT);
        ImmutableSortedMap.Builder<String, ModelProperty<?>> builder = ImmutableSortedMap.naturalOrder();
        for (ModelProperty<?> property : properties) {
            builder.put(property.getName(), property);
        }
        this.properties = builder.build();
        this.managedImpl = new WeakReference<Class<? extends T>>(managedImpl);
    }

    public ImmutableSortedMap<String, ModelProperty<?>> getProperties() {
        return properties;
    }

    public Class<? extends T> getManagedImpl() {
        return managedImpl.get();
    }
}
