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

import org.gradle.api.tasks.LocalState;
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyMetadata;

import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.OPTIONAL;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.REPLACES_EAGER_PROPERTY;

public class LocalStatePropertyAnnotationHandler extends AbstractPropertyAnnotationHandler {
    public LocalStatePropertyAnnotationHandler() {
        super(LocalState.class, Kind.OTHER, ModifierAnnotationCategory.annotationsOf(OPTIONAL, REPLACES_EAGER_PROPERTY));
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
        visitor.visitLocalStateProperty(value);
    }
}
