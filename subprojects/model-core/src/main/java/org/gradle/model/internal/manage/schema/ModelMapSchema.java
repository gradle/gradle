/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.model.internal.type.ModelType;

import java.lang.ref.WeakReference;

public class ModelMapSchema<T> extends ModelSchema<T> {
    private final WeakReference<Class<?>> managedImpl;
    private final ModelType<?> elementType;

    public ModelMapSchema(ModelType<T> type, ModelType<?> elementType, Class<?> managedImpl) {
        super(type, Kind.SPECIALIZED_MAP);
        this.elementType = elementType;
        this.managedImpl = new WeakReference<Class<?>>(managedImpl);
    }

    public ModelType<?> getElementType() {
        return elementType;
    }

    public Class<?> getManagedImpl() {
        return managedImpl.get();
    }
}
