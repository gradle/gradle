/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

public class NestedBeanAnnotationHandler extends AbstractPropertyAnnotationHandler {
    public NestedBeanAnnotationHandler(Collection<Class<? extends Annotation>> allowedModifiers) {
        super(Nested.class, Kind.OTHER, ImmutableSet.copyOf(allowedModifiers));
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        if (Map.class.isAssignableFrom(propertyMetadata.getDeclaredType().getRawType())) {
            Class<?> keyType = JavaReflectionUtil.extractNestedType((TypeToken<Map<?, ?>>) propertyMetadata.getDeclaredType(), Map.class, 0).getRawType();
            NestedValidationUtil.validateKeyType(validationContext, propertyMetadata.getPropertyName(), keyType);
        }
    }
}
