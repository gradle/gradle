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

import org.gradle.api.Nullable;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspect;
import org.gradle.model.internal.type.ModelType;

import java.lang.ref.WeakReference;

public class ModelManagedImplStructSchema<T> extends AbstractModelStructSchema<T> implements ManagedImplModelSchema<T> {
    private final WeakReference<Class<? extends T>> implementationType;
    private final WeakReference<Class<?>> delegateType;

    public ModelManagedImplStructSchema(ModelType<T> type, Iterable<ModelProperty<?>> properties, Iterable<ModelSchemaAspect> aspects, Class<? extends T> implementationType, @Nullable Class<?> delegateType) {
        super(type, properties, aspects);
        this.implementationType = new WeakReference<Class<? extends T>>(implementationType);
        this.delegateType = new WeakReference<Class<?>>(delegateType);
    }

    public Class<? extends T> getImplementationType() {
        return implementationType.get();
    }

    @Nullable
    public Class<?> getDelegateType() {
        return delegateType.get();
    }

    @Override
    public String toString() {
        return "managed " + getType();
    }
}
