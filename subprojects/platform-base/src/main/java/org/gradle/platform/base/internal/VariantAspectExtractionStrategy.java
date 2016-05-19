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

package org.gradle.platform.base.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Named;
import org.gradle.api.Nullable;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.extract.ModelPropertyExtractionResult;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractionResult;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractionStrategy;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractionContext;
import org.gradle.model.internal.manage.schema.extract.PropertyAccessorExtractionContext;
import org.gradle.platform.base.Variant;

import java.util.List;

public class VariantAspectExtractionStrategy implements ModelSchemaAspectExtractionStrategy {
    @Nullable
    @Override
    public ModelSchemaAspectExtractionResult extract(ModelSchemaExtractionContext<?> extractionContext, final List<ModelPropertyExtractionResult<?>> propertyResults) {
        ImmutableSet.Builder<ModelProperty<?>> dimensionsBuilder = ImmutableSet.builder();
        for (ModelPropertyExtractionResult<?> propertyResult : propertyResults) {
            ModelProperty<?> property = propertyResult.getProperty();
            for (PropertyAccessorExtractionContext accessor : propertyResult.getAccessors()) {
                if (accessor.isAnnotationPresent(Variant.class)) {
                    if (accessor.getAccessorType() == PropertyAccessorType.SETTER) {
                        throw invalidProperty(extractionContext, property, "@Variant annotation is only allowed on getter methods");
                    }
                    Class<?> propertyType = property.getType().getRawClass();
                    if (!String.class.equals(propertyType) && !Named.class.isAssignableFrom(propertyType)) {
                        throw invalidProperty(extractionContext, property, String.format("@Variant annotation only allowed for properties of type String and %s, but property has type %s", Named.class.getName(), propertyType.getName()));
                    }
                    dimensionsBuilder.add(property);
                }
            }
        }
        ImmutableSet<ModelProperty<?>> dimensions = dimensionsBuilder.build();
        if (dimensions.isEmpty()) {
            return null;
        }
        return new ModelSchemaAspectExtractionResult(new VariantAspect(dimensions));
    }

    protected InvalidManagedModelElementTypeException invalidProperty(ModelSchemaExtractionContext<?> extractionContext, ModelProperty<?> property, String message) {
        return new InvalidManagedModelElementTypeException(extractionContext, String.format("%s (invalid property: %s)", message, property.getName()));
    }
}
