/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.registry;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Nullable;
import org.gradle.internal.Factory;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.lang.annotation.Annotation;

public abstract class TypeModelRuleExtractor<A extends Annotation, T, U extends T> extends AbstractAnnotationDrivenComponentModelRuleExtractor<A> {

    private final String modelName;
    private final ModelType<T> baseInterface;
    private final ModelType<U> baseImplementation;
    private final ModelType<?> builderInterface;
    private final Factory<? extends TypeBuilderInternal<T>> typeBuilderFactory;

    public TypeModelRuleExtractor(String modelName, Class<T> baseInterface, Class<U> baseImplementation, Class<?> builderInterface, Factory<? extends TypeBuilderInternal<T>> typeBuilderFactory) {
        this.modelName = modelName;
        this.typeBuilderFactory = typeBuilderFactory;
        this.baseInterface = ModelType.of(baseInterface);
        this.baseImplementation = ModelType.of(baseImplementation);
        this.builderInterface = ModelType.of(builderInterface);
    }

    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
        try {
            ModelType<? extends T> type = readType(ruleDefinition);
            TypeBuilderInternal<T> builder = typeBuilderFactory.create();
            ruleDefinition.getRuleInvoker().invoke(builder);
            return createRegistration(ruleDefinition, type, builder);
        } catch (InvalidModelException e) {
            throw invalidModelRule(ruleDefinition, e);
        }
    }

    @Nullable
    protected abstract <R, S> ExtractedModelRule createRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelType<? extends T> type, TypeBuilderInternal<T> builder);

    protected ModelType<? extends T> readType(MethodRuleDefinition<?, ?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidModelException(String.format("Method %s must have a single parameter of type '%s'.", getDescription(), builderInterface.toString()));
        }
        ModelReference<?> subjectReference = ruleDefinition.getSubjectReference();
        @SuppressWarnings("ConstantConditions") ModelType<?> builder = subjectReference.getType();
        if (!builderInterface.isAssignableFrom(builder)) {
            throw new InvalidModelException(String.format("Method %s must have a single parameter of type '%s'.", getDescription(), builderInterface.toString()));
        }
        if (builder.getTypeVariables().size() != 1) {
            throw new InvalidModelException(String.format("Parameter of type '%s' must declare a type parameter.", builderInterface.toString()));
        }
        ModelType<?> subType = builder.getTypeVariables().get(0);

        if (subType.isWildcard()) {
            throw new InvalidModelException(String.format("%s type '%s' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", StringUtils.capitalize(modelName), subType.toString()));
        }

        ModelType<? extends T> asSubclass = baseInterface.asSubclass(subType);
        if (asSubclass == null) {
            throw new InvalidModelException(String.format("%s type '%s' is not a subtype of '%s'.", StringUtils.capitalize(modelName), subType.toString(), baseInterface.toString()));
        }

        return asSubclass;
    }

    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(String.format(" is not a valid %s model rule method.", modelName));
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    protected ModelType<? extends U> determineImplementationType(ModelType<? extends T> type, TypeBuilderInternal<T> builder) {
        Class<? extends T> implementation = builder.getDefaultImplementation();
        if (implementation == null) {
            return null;
        }

        ModelType<? extends T> implementationType = ModelType.of(implementation);
        ModelType<? extends U> asSubclass = baseImplementation.asSubclass(implementationType);

        if (asSubclass == null) {
            throw new InvalidModelException(String.format("%s implementation '%s' must extend '%s'.", StringUtils.capitalize(modelName), implementationType, baseImplementation));
        }

        if (!type.isAssignableFrom(asSubclass)) {
            throw new InvalidModelException(String.format("%s implementation '%s' must implement '%s'.", StringUtils.capitalize(modelName), asSubclass, type));
        }

        try {
            asSubclass.getRawClass().getConstructor();
        } catch (NoSuchMethodException nsmException) {
            throw new InvalidModelException(String.format("%s implementation '%s' must have public default constructor.", StringUtils.capitalize(modelName), asSubclass));
        }

        return asSubclass;
    }
}