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

import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.specs.Spec;
import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ScalarValueSchema;
import org.gradle.model.internal.manage.schema.SpecializedMapSchema;
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.extract.SpecializedMapNodeInitializer;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@NotThreadSafe
public class ManagedModelCreationRuleExtractor extends AbstractModelCreationRuleExtractor {
    private final ModelSchemaStore schemaStore;

    public ManagedModelCreationRuleExtractor(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }

    public String getDescription() {
        return String.format("%s and taking a managed model element", super.getDescription());
    }

    @Override
    public Spec<MethodRuleDefinition<?, ?>> getSpec() {
        final Spec<MethodRuleDefinition<?, ?>> superSpec = super.getSpec();
        return new Spec<MethodRuleDefinition<?, ?>>() {
            public boolean isSatisfiedBy(MethodRuleDefinition<?, ?> element) {
                return superSpec.isSatisfiedBy(element) && element.getReturnType().equals(ModelType.of(Void.TYPE));
            }
        };
    }

    @Override
    protected <T, S> void buildRegistration(MethodRuleDefinition<T, S> ruleDefinition, final ModelPath modelPath, ModelRegistrations.Builder registration) {
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        if (references.isEmpty()) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule has to take a managed model element instance as the first argument");
        }

        ModelType<T> modelType = Cast.uncheckedCast(references.get(0).getType());
        final ModelSchema<T> modelSchema = getModelSchema(modelType, ruleDefinition);
        if (modelSchema instanceof ScalarValueSchema) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule cannot take a value type as the first parameter, which is the element being created. Return the value from the method.");
        }

        List<ModelReference<?>> bindings = ruleDefinition.getReferences();
        List<ModelReference<?>> inputs = bindings.subList(1, bindings.size());
        final ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();

        if (modelSchema instanceof SpecializedMapSchema) {
            registration.actions(SpecializedMapNodeInitializer.getActions(ModelReference.of(modelPath), descriptor, (SpecializedMapSchema<T, ?>) modelSchema));
        } else {
            registration.action(ModelActionRole.Discover, Collections.singletonList(ModelReference.of(NodeInitializerRegistry.class)), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode node, List<ModelView<?>> modelViews) {
                    NodeInitializerRegistry nodeInitializerRegistry = (NodeInitializerRegistry) modelViews.get(0).getInstance();
                    NodeInitializer initializer = getNodeInitializer(descriptor, modelSchema, nodeInitializerRegistry);
                    for (Map.Entry<ModelActionRole, ModelAction> actionInRole : initializer.getActions(ModelReference.of(modelPath), descriptor).entries()) {
                        ModelActionRole role = actionInRole.getKey();
                        ModelAction action = actionInRole.getValue();
                        node.applyToSelf(role, action);
                    }
                }
            });
        }

        registration.action(ModelActionRole.Initialize, InputUsingModelAction.of(
            ModelReference.of(modelPath, modelType), descriptor, inputs, new RuleMethodBackedMutationAction<T>(ruleDefinition.getRuleInvoker())
        ));
    }

    private static NodeInitializer getNodeInitializer(ModelRuleDescriptor descriptor, ModelSchema<?> modelSchema, NodeInitializerRegistry nodeInitializerRegistry) {
        try {
            return nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forType(modelSchema.getType()));
        } catch (ModelTypeInitializationException e) {
            throw new InvalidModelRuleDeclarationException(descriptor, e);
        }
    }

    private <T> ModelSchema<T> getModelSchema(ModelType<T> managedType, MethodRuleDefinition<?, ?> ruleDefinition) {
        try {
            return schemaStore.getSchema(managedType);
        } catch (InvalidManagedModelElementTypeException e) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), e);
        }
    }
}
