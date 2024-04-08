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
import org.gradle.api.problems.internal.InternalProblemBuilder;
import org.gradle.api.problems.internal.Problem;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.parseBoolean;
import static java.util.Optional.ofNullable;

@NonNullApi
public class DefaultTypeAwareProblemBuilder extends DelegatingProblemBuilder implements TypeAwareProblemBuilder {

    public static final String TYPE_NAME = "typeName";
    public static final String PLUGIN_ID = "pluginId";
    public static final String PARENT_PROPERTY_NAME = "parentPropertyName";
    public static final String PROPERTY_NAME = "propertyName";
    public static final String TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE = "typeIsIrrelevantInErrorMessage";

    public DefaultTypeAwareProblemBuilder(InternalProblemBuilder problemBuilder) {
        super(problemBuilder);
    }

    @Override
    public TypeAwareProblemBuilder withAnnotationType(@Nullable Class<?> classWithAnnotationAttached) {
        if (classWithAnnotationAttached != null) {
            additionalData(TYPE_NAME, classWithAnnotationAttached.getName().replaceAll("\\$", "."));
        }
        return this;
    }

    @Override
    public TypeAwareProblemBuilder typeIsIrrelevantInErrorMessage() {
        additionalData(TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE, TRUE.toString());
        return this;
    }

    @Override
    public TypeAwareProblemBuilder forProperty(String propertyName) {
        additionalData(PROPERTY_NAME, propertyName);
        return this;
    }

    @Override
    public TypeAwareProblemBuilder parentProperty(@Nullable String parentProperty) {
        if (parentProperty == null) {
            return this;
        }
        String pp = getParentProperty(parentProperty);
        additionalData(PARENT_PROPERTY_NAME, pp);
        parentPropertyAdditionalData = pp;
        return this;
    }

    @Override
    public Problem build() {
        Problem problem = super.build();
        String prefix = introductionFor(problem.getAdditionalData());
        String text = Optional.ofNullable(problem.getContextualLabel()).orElseGet(() -> problem.getDefinition().getId().getDisplayName());
        return problem.toBuilder().contextualLabel(prefix + text).build();
    }

    public static String introductionFor(Map<String, Object> additionalMetadata) {
        Optional<String> rootType = ofNullable(additionalMetadata.get(TYPE_NAME))
            .map(Object::toString)
            .filter(DefaultTypeAwareProblemBuilder::shouldRenderType);
        Optional<DefaultPluginId> pluginId = ofNullable(additionalMetadata.get(PLUGIN_ID))
            .map(Object::toString)
            .map(DefaultPluginId::new);

        StringBuilder builder = new StringBuilder();
        boolean typeRelevant = rootType.isPresent() && !parseBoolean(additionalMetadata.getOrDefault(TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE, "").toString());
        if (typeRelevant) {
            if (pluginId.isPresent()) {
                builder.append("In plugin '")
                    .append(pluginId.get())
                    .append("' type '");
            } else {
                builder.append("Type '");
            }
            builder.append(rootType.get()).append("' ");
        }

        Object property = additionalMetadata.get(PROPERTY_NAME);
        if (property != null) {
            if (typeRelevant) {
                builder.append("property '");
            } else {
                if (pluginId.isPresent()) {
                    builder.append("In plugin '")
                        .append(pluginId.get())
                        .append("' property '");
                } else {
                    builder.append("Property '");
                }
            }
            ofNullable(additionalMetadata.get(PARENT_PROPERTY_NAME)).ifPresent(parentProperty -> {
                builder.append(parentProperty);
                builder.append('.');
            });
            builder.append(property)
                .append("' ");
        }
        return builder.toString();
    }

    // A heuristic to determine if the type is relevant or not.
    // The "DefaultTask" type may appear in error messages
    // (if using "adhoc" tasks) but isn't visible to this
    // class so we have to rely on text matching for now.
    private static boolean shouldRenderType(String className) {
        return !"org.gradle.api.DefaultTask".equals(className);
    }

    private String parentPropertyAdditionalData = null;

    private String getParentProperty(String parentProperty) {
        String existingParentProperty = parentPropertyAdditionalData;
        if (existingParentProperty == null) {
            return parentProperty;
        }
        return existingParentProperty + "." + parentProperty;
    }
}
