/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties.annotations;

import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.work.Incremental;

public abstract class AbstractInputFilePropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return !visitor.visitOutputFilePropertiesOnly();
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, BeanPropertyContext context) {
        PathSensitive pathSensitive = propertyMetadata.getAnnotation(PathSensitive.class);
        Class<? extends FileNormalizer> fileNormalizer = pathSensitive == null ? null : FileParameterUtils.determineNormalizerForPathSensitivity(pathSensitive.value());
        visitor.visitInputFileProperty(
            propertyName,
            propertyMetadata.isAnnotationPresent(Optional.class),
            propertyMetadata.isAnnotationPresent(SkipWhenEmpty.class),
            propertyMetadata.isAnnotationPresent(Incremental.class),
            fileNormalizer,
            value,
            getFilePropertyType()
        );
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, ParameterValidationContext visitor) {
    }

    protected abstract InputFilePropertyType getFilePropertyType();
}
