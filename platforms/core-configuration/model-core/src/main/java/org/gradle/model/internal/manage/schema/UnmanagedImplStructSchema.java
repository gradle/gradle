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

import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspect;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

public class UnmanagedImplStructSchema<T> extends AbstractStructSchema<T> {
    private final boolean annotated;

    public UnmanagedImplStructSchema(
        ModelType<T> type,
        Iterable<ModelProperty<?>> properties,
        Iterable<WeaklyTypeReferencingMethod<?, ?>> nonPropertyMethods,
        Iterable<ModelSchemaAspect> aspects,
        boolean annotated
    ) {
        super(type, properties, nonPropertyMethods, aspects);
        this.annotated = annotated;
    }

    public boolean isAnnotated() {
        return annotated;
    }

    @Override
    public String toString() {
        return "unmanaged " + getType();
    }
}
