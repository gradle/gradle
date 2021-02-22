/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

import org.gradle.api.internal.DocumentationRegistry;

public class DefaultPropertyValidationProblemBuilder extends AbstractValidationProblemBuilder<PropertyProblemBuilder> implements PropertyProblemBuilder{
    private String parentProperty;
    private String property;

    public DefaultPropertyValidationProblemBuilder(DocumentationRegistry documentationRegistry) {
        super(documentationRegistry);
    }

    @Override
    public PropertyProblemBuilder forProperty(String parentProperty, String property) {
        this.parentProperty = parentProperty;
        this.property = property;
        return this;
    }

    public TypeValidationProblem build() {
        if (shortProblemDescription == null) {
            throw new IllegalStateException("You must provide at least a short description of the problem");
        }
        return new TypeValidationProblem(
            problemId,
            severity,
            TypeValidationProblemLocation.forProperty(parentProperty, property),
            shortProblemDescription,
            longDescription,
            reason,
            TypeValidationProblem.Payload.of(cacheabilityProblemOnly),
            userManualReference,
            possibleSolutions
        );
    }
}
