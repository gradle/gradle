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

import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.inspect.AbstractAnnotationDrivenMethodRuleDefinitionHandler;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.platform.base.InvalidComponentModelException;

import java.lang.annotation.Annotation;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;

public abstract class AbstractAnnotationDrivenMethodComponentRuleDefinitionHandler<T extends Annotation> extends AbstractAnnotationDrivenMethodRuleDefinitionHandler<T> {
    protected <R> void assertIsVoidMethod(MethodRuleDefinition<R> ruleDefinition) {
        if (!ModelType.of(Void.TYPE).equals(ruleDefinition.getReturnType())) {
            throw new InvalidComponentModelException(String.format("%s method must not have a return value.", annotationType.getSimpleName()));
        }
    }

    protected <R, V> void visitCollectionBuilderSubject(RuleMethodDataCollector dataCollector, MethodRuleDefinition<R> ruleDefinition, Class<V> typeParameter) {
        if (ruleDefinition.getReferences().size() == 0) {
            throw new InvalidComponentModelException(String.format("%s method must have a parameter of type '%s'.", annotationType.getSimpleName(), CollectionBuilder.class.getName()));
        }

        ModelType<?> builder = ruleDefinition.getReferences().get(0).getType();

        if (!ModelType.of(CollectionBuilder.class).isAssignableFrom(builder)) {
            throw new InvalidComponentModelException(String.format("%s method first parameter must be of type '%s'.", annotationType.getSimpleName(), CollectionBuilder.class.getName()));
        }
        if (builder.getTypeVariables().size() != 1) {
            throw new InvalidComponentModelException(String.format("Parameter of type '%s' must declare a type parameter extending '%s'.", CollectionBuilder.class.getSimpleName(), typeParameter.getSimpleName()));
        }
        ModelType<?> subType = builder.getTypeVariables().get(0);

        if (subType.isWildcard()) {
            throw new InvalidComponentModelException(String.format("%s type '%s' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", typeParameter.getName(), subType.toString()));
        }
        dataCollector.parameterTypes.put(typeParameter, subType.getConcreteClass());
    }

    protected class RuleMethodDataCollector {
        private HashMap<Class<?>, Class<?>> parameterTypes = new HashMap<Class<?>, Class<?>>();

        @SuppressWarnings("unchecked")
        public <S, R extends S> Class<R> getParameterType(Class<S> baseClass) {
            return (Class<R>) parameterTypes.get(baseClass);
        }

        public <S> void put(Class<S> baseClass, Class<? extends S> concreteClass) {
            if(!baseClass.isAssignableFrom(concreteClass)){
                throw new InvalidParameterException(String.format("Class %s must be assignable from Class %s", baseClass.getName(), concreteClass.getName()));
            }
            parameterTypes.put(baseClass, concreteClass);
        }
    }

    protected <R, S> void visitDependency(RuleMethodDataCollector dataCollector, MethodRuleDefinition<R> ruleDefinition, ModelType<S> expectedDependency) {
        // TODO:DAZ Use ModelType.toString instead of getSimpleName()
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        ModelType<? extends S> dependency = null;
        for (ModelReference<?> reference : references) {
            ModelType<? extends S> newDependency = expectedDependency.asSubclass(reference.getType());
            if (newDependency != null) {
                if (dependency != null) {
                    throw new InvalidComponentModelException(String.format("%s method must have one parameter extending %s. Found multiple parameter extending %s.", annotationType.getSimpleName(),
                            expectedDependency.getConcreteClass().getSimpleName(),
                            expectedDependency.getConcreteClass().getSimpleName()));

                }
                dependency = newDependency;
            }
        }

        if (dependency == null) {
            throw new InvalidComponentModelException(String.format("%s method must have one parameter extending %s. Found no parameter extending %s.", annotationType.getSimpleName(),
                    expectedDependency.getConcreteClass().getSimpleName(),
                    expectedDependency.getConcreteClass().getSimpleName()));
        }
        dataCollector.put(expectedDependency.getConcreteClass(), dependency.getConcreteClass());
    }
}
