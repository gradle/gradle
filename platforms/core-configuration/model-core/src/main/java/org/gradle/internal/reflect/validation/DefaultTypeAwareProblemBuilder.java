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
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblemBuilder;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.api.problems.internal.TypeValidationDataSpec;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@NonNullApi
public class DefaultTypeAwareProblemBuilder extends DelegatingProblemBuilder implements TypeAwareProblemBuilder {
    private AdditionalDataBuilderFactory additionalDataBuilderFactory;

    public DefaultTypeAwareProblemBuilder(InternalProblemBuilder problemBuilder, AdditionalDataBuilderFactory additionalDataBuilderFactory) {
        super(problemBuilder);
        this.additionalDataBuilderFactory = additionalDataBuilderFactory;
    }

    @Override
    public TypeAwareProblemBuilder withAnnotationType(@Nullable Class<?> classWithAnnotationAttached) {
        if (classWithAnnotationAttached != null) {
            additionalData(TypeValidationDataSpec.class, data -> data.typeName(classWithAnnotationAttached.getName().replaceAll("\\$", ".")));
        }
        return this;
    }

    @Override
    public TypeAwareProblemBuilder forProperty(String propertyName) {
        additionalData(TypeValidationDataSpec.class, data -> data.propertyName(propertyName));
        return this;
    }

    @Override
    public TypeAwareProblemBuilder forFunction(String methodName) {
        additionalData(TypeValidationDataSpec.class, data -> data.functionName(methodName));
        return this;
    }

    @Override
    public TypeAwareProblemBuilder parentProperty(@Nullable String parentProperty) {
        if (parentProperty == null) {
            return this;
        }
        String pp = getParentProperty(parentProperty);
        additionalData(TypeValidationDataSpec.class, data -> data.parentPropertyName(pp));
        parentPropertyAdditionalData = pp;
        return this;
    }

    @Override
    public Problem build() {
        Problem problem = super.build();
        Optional<TypeValidationData> additionalData = Optional.ofNullable((TypeValidationData) problem.getAdditionalData());
        String prefix = introductionFor(additionalData, isTypeIrrelevantInErrorMessage(problem.getDefinition().getId()));
        String text = Optional.ofNullable(problem.getContextualLabel()).orElseGet(() -> problem.getDefinition().getId().getDisplayName());
        return ((InternalProblem) problem).toBuilder(additionalDataBuilderFactory).contextualLabel(prefix + text).build();
    }

    private static boolean isTypeIrrelevantInErrorMessage(ProblemId problemId) {
        if (!problemId.getGroup().equals(GradleCoreProblemGroup.validation().property())) {
            return false;
        } else {
            List<String> candidates = Arrays.asList("unknown-implementation", "unknown-implementation-nested", "implicit-dependency");
            return candidates.contains(problemId.getName());
        }
    }

    public static String introductionFor(Optional<TypeValidationData> additionalData, boolean typeIrrelevantInErrorMessage) {
        Optional<String> rootType = additionalData.map(TypeValidationData::getTypeName)
            .map(Object::toString)
            .filter(DefaultTypeAwareProblemBuilder::shouldRenderType);
        Optional<DefaultPluginId> pluginId = additionalData.map(TypeValidationData::getPluginId)
            .map(Object::toString)
            .map(DefaultPluginId::new);

        StringBuilder builder = new StringBuilder();
        boolean typeRelevant = rootType.isPresent() && !typeIrrelevantInErrorMessage;
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

        Object property = additionalData.map(TypeValidationData::getPropertyName).orElse(null);
        if (property != null) {
            renderPropertyIntro(additionalData, typeRelevant, builder, pluginId, property);
        }

        Object method = additionalData.map(TypeValidationData::getFunctionName).orElse(null);
        if (method != null) {
            renderMethodIntro(typeRelevant, builder, pluginId, method);
        }

        return builder.toString();
    }

    private static void renderPropertyIntro(Optional<TypeValidationData> additionalData, boolean typeRelevant, StringBuilder builder, Optional<DefaultPluginId> pluginId, Object property) {
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
        additionalData.map(TypeValidationData::getParentPropertyName).ifPresent(parentProperty -> {
            builder.append(parentProperty);
            builder.append('.');
        });
        builder.append(property)
            .append("' ");
    }

    private static void renderMethodIntro(boolean typeRelevant, StringBuilder builder, Optional<DefaultPluginId> pluginId, Object method) {
        if (typeRelevant) {
            builder.append("method '");
        } else {
            if (pluginId.isPresent()) {
                builder.append("In plugin '")
                    .append(pluginId.get())
                    .append("' method '");
            } else {
                builder.append("Method '");
            }
        }
        builder.append(method)
            .append("' ");
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
