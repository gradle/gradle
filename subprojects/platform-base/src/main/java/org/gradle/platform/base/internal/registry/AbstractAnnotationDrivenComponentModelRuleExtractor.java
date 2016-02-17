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

import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.inspect.AbstractAnnotationDrivenModelRuleExtractor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceValidationProblemCollector;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;

public abstract class AbstractAnnotationDrivenComponentModelRuleExtractor<T extends Annotation> extends AbstractAnnotationDrivenModelRuleExtractor<T> {
    protected <V> void visitSubject(RuleMethodDataCollector dataCollector, MethodRuleDefinition<?, ?> ruleDefinition, ModelType<V> typeParameter, RuleSourceValidationProblemCollector problems) {
        if (ruleDefinition.getReferences().size() == 0) {
            problems.add(ruleDefinition, "A method " + getDescription() + " must have at least two parameters.");
            return;
        }

        ModelType<?> subjectType = ruleDefinition.getSubjectReference().getType();
        if (!isModelMap(subjectType)) {
            problems.add(ruleDefinition, String.format("The first parameter of a method %s must be of type %s.", getDescription(), ModelMap.class.getName()));
            return;
        }

        List<ModelType<?>> typeVariables = subjectType.getTypeVariables();
        if (typeVariables.size() != 1) {
            problems.add(ruleDefinition, String.format("Parameter of type %s must declare a type parameter extending %s.", ModelMap.class.getSimpleName(), typeParameter.getDisplayName()));
            return;
        }

        ModelType<?> elementType = typeVariables.get(0);
        if (elementType.isWildcard()) {
            problems.add(ruleDefinition, String.format("%s type %s cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", typeParameter.getDisplayName(), elementType.getDisplayName()));
            return;
        }
        dataCollector.parameterTypes.put(typeParameter, elementType);
    }

    private boolean isModelMap(ModelType<?> modelType) {
        return ModelType.of(ModelMap.class).isAssignableFrom(modelType);
    }

    protected class RuleMethodDataCollector {
        private HashMap<ModelType<?>, ModelType<?>> parameterTypes = new HashMap<ModelType<?>, ModelType<?>>();

        @SuppressWarnings("unchecked")
        public <S, R extends S> ModelType<R> getParameterType(ModelType<S> baseClass) {
            return (ModelType<R>) parameterTypes.get(baseClass);
        }

        public <S> void put(ModelType<S> baseClass, ModelType<? extends S> concreteClass) {
            parameterTypes.put(baseClass, concreteClass);
        }
    }

    protected <S> void visitDependency(RuleMethodDataCollector dataCollector, MethodRuleDefinition<?, ?> ruleDefinition, ModelType<S> expectedDependency, RuleSourceValidationProblemCollector problems) {
        if (ruleDefinition.getReferences().isEmpty() && problems.hasProblems()) {
            return;
        }

        List<ModelReference<?>> references = ruleDefinition.getReferences();
        ModelType<? extends S> dependency = null;
        for (ModelReference<?> reference : references) {
            if (expectedDependency.isAssignableFrom(reference.getType())) {
                if (dependency != null) {
                    problems.add(ruleDefinition, String.format("A method %s must have one parameter extending %s. Found multiple parameter extending %s.", getDescription(),
                                                expectedDependency.getDisplayName(),
                                                expectedDependency.getDisplayName()));
                    return;
                }
                dependency = reference.getType().asSubtype(expectedDependency);
            }
        }

        if (dependency == null) {
            problems.add(ruleDefinition, String.format("A method %s must have one parameter extending %s. Found no parameter extending %s.", getDescription(),
                    expectedDependency.getDisplayName(),
                    expectedDependency.getDisplayName()));
            return;
        }
        dataCollector.put(expectedDependency, dependency);
    }
}
