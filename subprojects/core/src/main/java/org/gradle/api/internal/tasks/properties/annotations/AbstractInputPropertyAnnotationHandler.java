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

import org.gradle.api.internal.tasks.DeclaredTaskInputFileProperty;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;

import java.io.File;

public abstract class AbstractInputPropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return !visitor.visitOutputFilePropertiesOnly();
    }

    @Override
    public void visitPropertyValue(PropertyValue propertyValue, PropertyVisitor visitor, PropertySpecFactory specFactory, BeanPropertyContext context) {
        PathSensitive pathSensitive = propertyValue.getAnnotation(PathSensitive.class);
        final PathSensitivity pathSensitivity;
        if (pathSensitive == null) {
            // If this default is ever changed, ensure the documentation on PathSensitive is updated as well as this guide:
            // https://guides.gradle.org/using-build-cache/#relocatability
            pathSensitivity = PathSensitivity.ABSOLUTE;
        } else {
            pathSensitivity = pathSensitive.value();
        }
        DeclaredTaskInputFileProperty fileSpec = createFileSpec(propertyValue, specFactory);
        fileSpec
            .withPropertyName(propertyValue.getPropertyName())
            .withPathSensitivity(pathSensitivity)
            .skipWhenEmpty(propertyValue.isAnnotationPresent(SkipWhenEmpty.class))
            .optional(propertyValue.isOptional());
        visitor.visitInputFileProperty(fileSpec);
    }

    protected abstract DeclaredTaskInputFileProperty createFileSpec(PropertyValue propertyValue, PropertySpecFactory specFactory);

    protected static File toFile(TaskValidationContext context, Object value) {
        return context.getResolver().resolve(value);
    }
}
