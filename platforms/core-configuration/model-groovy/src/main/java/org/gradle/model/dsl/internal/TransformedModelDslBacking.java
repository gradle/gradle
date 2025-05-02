/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.dsl.internal;

import groovy.lang.Closure;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.dsl.internal.transform.ClosureBackedRuleFactory;
import org.gradle.model.dsl.internal.transform.RulesBlock;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import static org.gradle.model.internal.core.DefaultNodeInitializerRegistry.DEFAULT_REFERENCE;
import static org.gradle.model.internal.core.NodeInitializerContext.forType;

@ThreadSafe
public class TransformedModelDslBacking {
    private final ModelRegistry modelRegistry;
    private final ClosureBackedRuleFactory ruleFactory;

    public TransformedModelDslBacking(ModelRegistry modelRegistry, RelativeFilePathResolver relativeFilePathResolver) {
        this.modelRegistry = modelRegistry;
        this.ruleFactory = new ClosureBackedRuleFactory(relativeFilePathResolver);
    }

    /**
     * Invoked by transformed DSL configuration rules
     */
    public void configure(String modelPathString, Closure<?> closure) {
        ModelPath modelPath = ModelPath.path(modelPathString);
        DeferredModelAction modelAction = ruleFactory.toAction(Object.class, closure);
        registerAction(modelPath, ModelType.UNTYPED, ModelActionRole.Mutate, modelAction);
    }

    /**
     * Invoked by transformed DSL creation rules
     */
    public <T> void create(String modelPathString, Class<T> type, Closure<?> closure) {
        ModelPath modelPath = ModelPath.path(modelPathString);
        DeferredModelAction modelAction = ruleFactory.toAction(type, closure);
        ModelRuleDescriptor descriptor = modelAction.getDescriptor();
        ModelType<T> modelType = ModelType.of(type);
        try {
            NodeInitializerRegistry nodeInitializerRegistry = modelRegistry.realize(DEFAULT_REFERENCE.getPath(), DEFAULT_REFERENCE.getType());
            NodeInitializer nodeInitializer = nodeInitializerRegistry.getNodeInitializer(forType(modelType));
            modelRegistry.register(ModelRegistrations.of(modelPath, nodeInitializer).descriptor(descriptor).build());
        } catch (ModelTypeInitializationException e) {
            throw new InvalidModelRuleDeclarationException(descriptor, e);
        }
        registerAction(modelPath, modelType, ModelActionRole.Initialize, modelAction);
    }

    private <T> void registerAction(ModelPath modelPath, ModelType<T> viewType, final ModelActionRole role, final DeferredModelAction action) {
        ModelReference<T> reference = ModelReference.of(modelPath, viewType);
        modelRegistry.configure(ModelActionRole.Initialize, DirectNodeNoInputsModelAction.of(reference, action.getDescriptor(), new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode node) {
                action.execute(node, role);
            }
        }));
    }

    public static boolean isTransformedBlock(Closure<?> closure) {
        Class<?> closureClass = closure.getClass();
        RulesBlock annotation = closureClass.getAnnotation(RulesBlock.class);
        return annotation != null;
    }
}
