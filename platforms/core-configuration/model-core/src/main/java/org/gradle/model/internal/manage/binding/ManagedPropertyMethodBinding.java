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

import com.google.common.base.Preconditions;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

/**
 * Method binding for methods implemented by a managed property.
 */
public class ManagedPropertyMethodBinding extends AbstractStructMethodBinding {
    private final String propertyName;

    public ManagedPropertyMethodBinding(WeaklyTypeReferencingMethod<?, ?> source, String propertyName, PropertyAccessorType accessorType) {
        super(source, Preconditions.checkNotNull(accessorType));
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public ModelType<?> getDeclaredPropertyType() {
        PropertyAccessorType accessorType = getAccessorType();
        assert accessorType != null;
        return ModelType.of(accessorType.genericPropertyTypeFor(getViewMethod().getMethod()));
    }

    @Override
    public String toString() {
        PropertyAccessorType accessorType = getAccessorType();
        assert accessorType != null;
        return getViewMethod() + "/" + accessorType.name();
    }
}
