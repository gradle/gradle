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
package org.gradle.internal.properties.annotations;

import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Handles validation, dependency handling, and skipping for a property marked with a given annotation.
 *
 * <p>Each handler must be registered as a global service.</p>
 */
@ServiceScope(Scope.Global.class)
public interface PropertyAnnotationHandler extends AnnotationHandler {
    /**
     * Does this handler do something useful with the properties that match it? Or can these properties be ignored?
     *
     * Should consider splitting up this type, perhaps into something that inspects the properties and produces the actual handlers and validation problems.
     */
    boolean isPropertyRelevant();

    /**
     * Visit the value of a property with this annotation attached.
     */
    void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor);

    /**
     * Visits problems associated with the given property, if any.
     */
    default void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {}

    /**
     * Returns the kind of properties this handler handles.
     */
    Kind getKind();

    enum Kind {
        INPUT, OUTPUT, OTHER
    }
}
