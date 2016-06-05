/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding;

import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;

/**
 * Binds a method declared in views to its actual implementation.
 */
public abstract class AbstractStructMethodBinding implements StructMethodBinding {
    private final WeaklyTypeReferencingMethod<?, ?> viewMethod;
    private final PropertyAccessorType accessorType;

    public AbstractStructMethodBinding(WeaklyTypeReferencingMethod<?, ?> viewMethod, PropertyAccessorType accessorType) {
        this.viewMethod = viewMethod;
        this.accessorType = accessorType;
    }

    @Override
    public WeaklyTypeReferencingMethod<?, ?> getViewMethod() {
        return viewMethod;
    }

    @Override
    public PropertyAccessorType getAccessorType() {
        return accessorType;
    }

    @Override
    abstract public String toString();
}
