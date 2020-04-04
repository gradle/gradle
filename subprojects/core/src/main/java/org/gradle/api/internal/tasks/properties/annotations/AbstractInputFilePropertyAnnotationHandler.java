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
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.TypeValidationContext;
import org.gradle.work.Incremental;

import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.NORMALIZATION;
import static org.gradle.internal.reflect.TypeValidationContext.Severity.CACHEABILITY_WARNING;

public abstract class AbstractInputFilePropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, BeanPropertyContext context) {
        Annotation fileNormalization = propertyMetadata.getAnnotationForCategory(NORMALIZATION);
        Class<? extends FileNormalizer> fileNormalizer;
        if (fileNormalization == null) {
            fileNormalizer = null;
        } else if (fileNormalization instanceof PathSensitive) {
            PathSensitivity pathSensitivity = ((PathSensitive) fileNormalization).value();
            fileNormalizer = FileParameterUtils.determineNormalizerForPathSensitivity(pathSensitivity);
        } else if (fileNormalization instanceof Classpath) {
            fileNormalizer = ClasspathNormalizer.class;
        } else if (fileNormalization instanceof CompileClasspath) {
            fileNormalizer = CompileClasspathNormalizer.class;
        } else {
            throw new IllegalStateException("Unknown normalization annotation used: " + fileNormalization);
        }
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
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        if (!propertyMetadata.hasAnnotationForCategory(NORMALIZATION)) {
            validationContext.visitPropertyProblem(CACHEABILITY_WARNING,
                propertyMetadata.getPropertyName(),
                "is declared without normalization specified. Properties of cacheable work must declare their normalization via @PathSensitive, @Classpath or @CompileClasspath. Defaulting to PathSensitivity.ABSOLUTE"
            );
        }
    }

    protected abstract InputFilePropertyType getFilePropertyType();
}
