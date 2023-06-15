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
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

public class DefaultPropertyValidationProblemBuilder extends AbstractValidationProblemBuilder<PropertyProblemBuilder> implements PropertyProblemBuilderInternal {
    private Class<?> rootType;
    private String parentProperty;
    private String property;

    public DefaultPropertyValidationProblemBuilder(DocumentationRegistry documentationRegistry, @Nullable PluginId pluginId) {
        super(documentationRegistry, pluginId);
    }

    @Override
    public PropertyProblemBuilder forProperty(String parentProperty, String property) {
        this.parentProperty = parentProperty;
        this.property = property;
        return this;
    }

    @Override
    public PropertyProblemBuilder forOwner(@Nullable String parentProperty) {
        if (parentProperty == null) {
            return this;
        }
        if (property == null) {
            throw new IllegalStateException("Calling this method doesn't make sense if the property isn't set");
        }
        if (this.parentProperty == null) {
            this.parentProperty = parentProperty;
        } else {
            this.parentProperty = parentProperty + "." + this.parentProperty;
        }
        return this;
    }

    @Override
    public PropertyProblemBuilder forType(@Nullable Class<?> rootType) {
        this.rootType = rootType;
        return this;
    }

    public TypeValidationProblem build() {
        if (problemId == null) {
            throw new IllegalStateException("You must set the problem id");
        }
        if (shortProblemDescription == null) {
            throw new IllegalStateException("You must provide at least a short description of the problem");
        }
        if (userManualReference == null) {
            throw new IllegalStateException("You must provide a user manual reference");
        }
        return new TypeValidationProblem(
            problemId,
            severity,
            TypeValidationProblemLocation.forProperty(typeIrrelevantInErrorMessage ? null : rootType, typeIrrelevantInErrorMessage ? null : pluginId, parentProperty, property),
            shortProblemDescription,
            longDescription,
            reason,
            userManualReference,
            possibleSolutions
        );
    }

}
