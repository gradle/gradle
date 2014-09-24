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

import org.gradle.api.Task;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTask;
import org.gradle.platform.base.InvalidComponentModelException;

import java.util.List;

public class BinaryTaskRuleDefinitionHandler extends AbstractAnnotationDrivenMethodComponentRuleDefinitionHandler<BinaryTask> {

    public <R> void register(final MethodRuleDefinition<R> ruleDefinition, final ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            verifyMethodSignature(ruleDefinition);
       } catch (InvalidComponentModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    //TODO extract common general method reusable by all AnnotationRuleDefinitionHandler
    protected <R> void invalidModelRule(MethodRuleDefinition<R> ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid BinaryTask model rule method.");
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private <R> void verifyMethodSignature(MethodRuleDefinition<R> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        assertHasCollectionBuilderSubject(ruleDefinition, Task.class);
        assertHasDependency(ruleDefinition, BinarySpec.class);
    }

    private <R> Class<?> assertHasDependency(MethodRuleDefinition<R> ruleDefinition, Class<?> expectedDependencyClass) {
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        Class<?> dependencyClass = null;
        for (ModelReference<?> reference : references) {
            if (expectedDependencyClass.isAssignableFrom(reference.getType().getConcreteClass())) {
                if (dependencyClass != null) {
                    throw new InvalidComponentModelException(String.format("%s method must have one parameter extending %s. Found multiple parameter extending %s.", annotationType.getSimpleName(),
                            expectedDependencyClass.getSimpleName(),
                            expectedDependencyClass.getSimpleName()));
                }
                dependencyClass = reference.getType().getConcreteClass();
            }
        }
        return dependencyClass;
    }
}
