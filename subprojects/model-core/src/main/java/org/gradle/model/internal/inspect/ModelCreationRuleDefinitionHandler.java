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

package org.gradle.model.internal.inspect;

import org.gradle.api.Transformer;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.Model;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;

import java.util.List;

public class ModelCreationRuleDefinitionHandler extends AbstractAnnotationDrivenMethodRuleDefinitionHandler<Model> {
    public <T> void register(MethodRuleDefinition<T> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {

        // TODO validate model name
        String modelName = determineModelName(ruleDefinition);

        try {
            ModelPath.validatePath(modelName);
        } catch (Exception e) {
            throw new InvalidModelRuleDeclarationException(String.format("Path of declared model element created by rule %s is invalid.", ruleDefinition.getDescriptor()), e);
        }

        // TODO validate the return type (generics?)
        ModelType<T> returnType = ruleDefinition.getReturnType();
        ModelPath path = ModelPath.path(modelName);
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();

        Transformer<T, Inputs> transformer = new ModelRuleInvokerBackedTransformer<T>(ruleDefinition.getRuleInvoker(), descriptor, references);
        modelRegistry.create(
                ModelCreators.of(ModelReference.of(path, returnType), transformer)
                        .descriptor(descriptor)
                        .inputs(references)
                        .build()
        );
    }

    private String determineModelName(MethodRuleDefinition<?> ruleDefinition) {
        String annotationValue = ruleDefinition.getAnnotation(Model.class).value();
        if (annotationValue == null || annotationValue.isEmpty()) {
            return ruleDefinition.getMethodName();
        } else {
            return annotationValue;
        }
    }

    private static class ModelRuleInvokerBackedTransformer<T> implements Transformer<T, Inputs> {

        private final ModelRuleDescriptor descriptor;
        private final ModelRuleInvoker<T> ruleInvoker;
        private final List<ModelReference<?>> inputReferences;

        private ModelRuleInvokerBackedTransformer(ModelRuleInvoker<T> ruleInvoker, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputReferences) {
            this.descriptor = descriptor;
            this.ruleInvoker = ruleInvoker;
            this.inputReferences = inputReferences;
        }

        public T transform(Inputs inputs) {
            T instance;
            if (inputs.size() == 0) {
                instance = ruleInvoker.invoke();
            } else {
                Object[] args = new Object[inputs.size()];
                for (int i = 0; i < inputs.size(); i++) {
                    args[i] = inputs.get(i, inputReferences.get(i).getType()).getInstance();
                }

                instance = ruleInvoker.invoke(args);
            }
            if (instance == null) {
                throw new ModelRuleExecutionException(descriptor, "rule returned null");
            }
            return instance;
        }
    }
}
