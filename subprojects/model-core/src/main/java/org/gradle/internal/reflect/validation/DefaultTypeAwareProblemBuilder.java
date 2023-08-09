/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.internal.DefaultProblemBuilder;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;

import javax.annotation.Nullable;

import static java.lang.Boolean.TRUE;

@NonNullApi
public class DefaultTypeAwareProblemBuilder extends DefaultProblemBuilder implements TypeAwareProblemBuilder {

    public static final String TYPE_NAME = "typeName";
    public static final String PLUGIN_ID = "pluginId";
    public static final String PARENT_PROPERTY_NAME = "parentPropertyName";
    public static final String PROPERTY_NAME = "propertyName";
    public static final String TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE = "typeIsIrrelevantInErrorMessage";

    public DefaultTypeAwareProblemBuilder(BuildOperationProgressEventEmitter buildOperationProgressEventEmitter, Problems problems) {
        super(problems, buildOperationProgressEventEmitter);
    }

    @Override
    public TypeAwareProblemBuilder withAnnotationType(@Nullable Class<?> classWithAnnotationAttached) {
        if (classWithAnnotationAttached != null) {
            withMetadata(TYPE_NAME, classWithAnnotationAttached.getName().replaceAll("\\$", "."));
        }
        return this;
    }

    @Override
    public TypeAwareProblemBuilder typeIsIrrelevantInErrorMessage() {
        withMetadata(TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE, TRUE.toString());
        return this;
    }

    @Override
    public TypeAwareProblemBuilder forProperty(String propertyName) {
        withMetadata(PROPERTY_NAME, propertyName);
        return this;
    }

    @Override
    public TypeAwareProblemBuilder parentProperty(@Nullable String parentProperty) {
        if (parentProperty == null) {
            return this;
        }
        withMetadata(PARENT_PROPERTY_NAME, getParentProperty(parentProperty));
        return this;
    }

    private String getParentProperty(String parentProperty) {
        String existingParentProperty = additionalMetadata.get(PARENT_PROPERTY_NAME);
        if (existingParentProperty == null) {
            return parentProperty;
        }
        return existingParentProperty + "." + parentProperty;
    }
}
