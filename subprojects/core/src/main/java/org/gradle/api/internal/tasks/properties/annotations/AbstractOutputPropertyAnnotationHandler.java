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

import org.gradle.api.internal.tasks.DeclaredTaskOutputFileProperty;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;

public abstract class AbstractOutputPropertyAnnotationHandler implements PropertyAnnotationHandler {

    protected abstract DeclaredTaskOutputFileProperty createFileSpec(PropertyValue propertyValue, PropertySpecFactory specFactory);

    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return true;
    }

    @Override
    public void visitPropertyValue(PropertyValue propertyValue, PropertyVisitor visitor, PropertySpecFactory specFactory, BeanPropertyContext context) {
        DeclaredTaskOutputFileProperty fileSpec = createFileSpec(propertyValue, specFactory);
        fileSpec
            .withPropertyName(propertyValue.getPropertyName())
            .optional(propertyValue.isOptional());
        visitor.visitOutputFileProperty(fileSpec);
    }
}
