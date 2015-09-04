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

package org.gradle.model.internal.manage.schema.extract;

import org.gradle.api.Nullable;
import org.gradle.model.internal.manage.schema.ModelProperty;

public class ModelPropertyExtractionResult<T> {
    private final ModelProperty<T> property;
    private final PropertyAccessorExtractionContext getter;
    private final PropertyAccessorExtractionContext setter;

    public ModelPropertyExtractionResult(ModelProperty<T> property, PropertyAccessorExtractionContext getter, @Nullable PropertyAccessorExtractionContext setter) {
        this.property = property;
        this.getter = getter;
        this.setter = setter;
    }

    public ModelProperty<T> getProperty() {
        return property;
    }

    public PropertyAccessorExtractionContext getGetter() {
        return getter;
    }

    @Nullable
    public PropertyAccessorExtractionContext getSetter() {
        return setter;
    }
}
