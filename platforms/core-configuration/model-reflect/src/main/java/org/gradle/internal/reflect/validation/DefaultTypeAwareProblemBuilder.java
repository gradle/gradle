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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.problems.GradleProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemBuilderInternal;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.api.problems.internal.TypeValidationDataSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@NullMarked
public class DefaultTypeAwareProblemBuilder extends DelegatingProblemBuilder implements TypeAwareProblemBuilder {

    // Names of the Build Logic problems whose message does not mention the type of the work.
    // The producers use these constants, so the names cannot drift apart.
    public static final String UNKNOWN_IMPLEMENTATION = "Unknown implementation";
    public static final String UNKNOWN_PROPERTY_IMPLEMENTATION = "Unknown property implementation";
    public static final String IMPLICIT_DEPENDENCY = "Property has implicit dependency";

    private static final ImmutableSet<String> TYPE_IRRELEVANT_PROBLEMS = ImmutableSet.of(UNKNOWN_IMPLEMENTATION, UNKNOWN_PROPERTY_IMPLEMENTATION, IMPLICIT_DEPENDENCY);

    private final GradleProblemGroup gradleGroup;

    public DefaultTypeAwareProblemBuilder(ProblemBuilderInternal problemBuilder, GradleProblemGroup gradleGroup) {
        super(problemBuilder);
        this.gradleGroup = gradleGroup;
    }

    @Override
    public GradleProblemGroup getGradleGroup() {
        return gradleGroup;
    }

    @Override
    public TypeAwareProblemBuilder withAnnotationType(@Nullable Class<?> classWithAnnotationAttached) {
        if (classWithAnnotationAttached != null) {
            this.additionalDataInternal(TypeValidationDataSpec.class, data -> data.typeName(classWithAnnotationAttached.getName().replaceAll("\\$", ".")));
        }
        return this;
    }

    @Override
    public TypeAwareProblemBuilder forProperty(String propertyName) {
        this.additionalDataInternal(TypeValidationDataSpec.class, data -> data.propertyName(propertyName));
        return this;
    }

    @Override
    public TypeAwareProblemBuilder forFunction(String methodName) {
        this.additionalDataInternal(TypeValidationDataSpec.class, data -> data.functionName(methodName));
        return this;
    }

    @Override
    public TypeAwareProblemBuilder parentProperty(@Nullable String parentProperty) {
        if (parentProperty == null) {
            return this;
        }
        String pp = getParentProperty(parentProperty);
        this.additionalDataInternal(TypeValidationDataSpec.class, data -> data.parentPropertyName(pp));
        parentPropertyAdditionalData = pp;
        return this;
    }

    @Override
    public ProblemInternal build() {
        ProblemInternal problem = super.build();
        Optional<TypeValidationData> additionalData = Optional.ofNullable((TypeValidationData) problem.getAdditionalData());
        String prefix = introductionFor(additionalData, isTypeIrrelevantInErrorMessage(problem.getDefinition().getId()));
        String text = Optional.ofNullable(problem.getContextualLabel()).orElseGet(() -> problem.getDefinition().getId().getDisplayName());
        return problem.toBuilder(getInfrastructure()).contextualLabel(prefix + text).build();
    }

    private boolean isTypeIrrelevantInErrorMessage(ProblemId problemId) {
        return problemId.getGroup().equals(gradleGroup.getBuildLogic()) && TYPE_IRRELEVANT_PROBLEMS.contains(problemId.getName());
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
