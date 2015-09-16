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
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ModelValueSchema;
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@NotThreadSafe
public class ManagedModelCreationRuleExtractor extends AbstractModelCreationRuleExtractor {
    private final ModelSchemaStore schemaStore;
    private final NodeInitializerRegistry nodeInitializerRegistry;

    public ManagedModelCreationRuleExtractor(ModelSchemaStore schemaStore, NodeInitializerRegistry nodeInitializerRegistry) {
        this.schemaStore = schemaStore;
        this.nodeInitializerRegistry = nodeInitializerRegistry;
    }

    public String getDescription() {
        return String.format("@%s and taking a managed model element", super.getDescription());
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
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
        String modelName = determineModelName(ruleDefinition);

        List<ModelReference<?>> references = ruleDefinition.getReferences();
        if (references.isEmpty()) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule has to take a managed model element instance as the first argument");
        }

        ModelType<?> managedType = references.get(0).getType();
        return new ExtractedModelCreator(buildModelCreatorForManagedType(managedType, ruleDefinition, ModelPath.path(modelName)));
    }

    private <T> ModelCreator buildModelCreatorForManagedType(ModelType<T> managedType, final MethodRuleDefinition<?, ?> ruleDefinition, ModelPath modelPath) {
        ModelSchema<T> modelSchema = getModelSchema(managedType, ruleDefinition);

        if (modelSchema instanceof ModelValueSchema) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule cannot take a value type as the first parameter, which is the element being created. Return the value from the method.");
        }
        NodeInitializer nodeInitializer = null;
        try {
            nodeInitializer = nodeInitializerRegistry.getNodeInitializer(modelSchema);
        } catch (ModelTypeInitializationException e) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), e);
        }

        List<ModelReference<?>> bindings = ruleDefinition.getReferences();
        List<ModelReference<?>> inputs = bindings.subList(1, bindings.size());
        ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();

        final ModelReference<T> reference = ModelReference.of(modelPath, managedType);
        return ModelCreators.of(modelPath, nodeInitializer)
            .descriptor(descriptor)
            .action(ModelActionRole.Initialize, InputUsingModelAction.of(
                    reference, descriptor, inputs, new RuleMethodBackedMutationAction<T>(ruleDefinition.getRuleInvoker())
                )
            )
            .build();
    }

    private <T> ModelSchema<T> getModelSchema(ModelType<T> managedType, MethodRuleDefinition<?, ?> ruleDefinition) {
        try {
            return schemaStore.getSchema(managedType);
        } catch (InvalidManagedModelElementTypeException e) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), e);
        }
    }
}
