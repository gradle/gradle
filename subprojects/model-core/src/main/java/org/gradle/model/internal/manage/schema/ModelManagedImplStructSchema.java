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

import com.google.common.base.Function;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspect;
import org.gradle.model.internal.type.ModelType;

import java.lang.ref.WeakReference;

public class ModelManagedImplStructSchema<T> extends AbstractModelStructSchema<T> implements ManagedImplModelSchema<T> {
    private final WeakReference<Class<? extends T>> managedImpl;
    private final WeakReference<Class<?>> delegateType;
    private final NodeInitializer nodeInitializer;

    public ModelManagedImplStructSchema(ModelType<T> type, Iterable<ModelProperty<?>> properties, Iterable<ModelSchemaAspect> aspects, Class<? extends T> managedImpl, @Nullable Class<?> delegateType, Function<? super ModelManagedImplStructSchema<T>, NodeInitializer> nodeInitializer) {
        super(type, properties, aspects);
        this.nodeInitializer = nodeInitializer.apply(this);
        this.managedImpl = new WeakReference<Class<? extends T>>(managedImpl);
        this.delegateType = new WeakReference<Class<?>>(delegateType);
    }

    public Class<? extends T> getManagedImpl() {
        return managedImpl.get();
    }

    @Nullable
    public Class<?> getDelegateType() {
        return delegateType.get();
    }

    @Override
    public NodeInitializer getNodeInitializer() {
        return nodeInitializer;
    }

    @Override
    public String toString() {
        return "managed " + getType();
    }
}
