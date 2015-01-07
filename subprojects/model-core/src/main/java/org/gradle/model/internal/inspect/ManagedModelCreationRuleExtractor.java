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
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelInstantiator;
import org.gradle.model.internal.manage.instance.strategy.StrategyBackedModelInstantiator;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@NotThreadSafe
public class ManagedModelCreationRuleExtractor extends AbstractModelCreationRuleExtractor {

    private final ModelSchemaStore schemaStore;
    private final ManagedProxyFactory proxyFactory = new ManagedProxyFactory();
    private final ModelInstantiator modelInstantiator;

    public ManagedModelCreationRuleExtractor(ModelSchemaStore schemaStore, Instantiator instantiator) {
        this.schemaStore = schemaStore;
        this.modelInstantiator = new StrategyBackedModelInstantiator(schemaStore, proxyFactory, instantiator);
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
    public <R, S> ModelRuleRegistration registration(MethodRuleDefinition<R, S> ruleDefinition) {
        String modelName = determineModelName(ruleDefinition);

        List<ModelReference<?>> references = ruleDefinition.getReferences();
        if (references.isEmpty()) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule has to take a managed model element instance as the first argument");
        }

        ModelType<?> managedType = references.get(0).getType();
        return new ModelCreatorRegistration(buildModelCreatorForManagedType(managedType, ruleDefinition, ModelPath.path(modelName)));
    }

    private <T> ModelCreator buildModelCreatorForManagedType(ModelType<T> managedType, final MethodRuleDefinition<?, ?> ruleDefinition, ModelPath modelPath) {
        ModelSchema<T> modelSchema = getModelSchema(managedType, ruleDefinition);

        if (modelSchema.getKind().equals(ModelSchema.Kind.VALUE)) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule cannot take a value type as the first parameter, which is the element being created. Return the value from the method.");
        }

        if (!modelSchema.getKind().isManaged()) {
            String description = String.format("a void returning model element creation rule has to take an instance of a managed type as the first argument");
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), description);
        }

        List<ModelReference<?>> bindings = ruleDefinition.getReferences();
        List<ModelReference<?>> inputs = bindings.subList(1, bindings.size());
        ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();

        BiAction<MutableModelNode, Inputs> initializer;
        ModelProjection projection;

        if (managedType.getRawClass().equals(ManagedSet.class)) {
            initializer = new ManagedModelRuleInvokerInstanceBackedTransformer<T>(modelSchema, modelInstantiator, ruleDefinition.getRuleInvoker(), descriptor, inputs);
            projection = ManagedSetModelProjection.of(managedType.getTypeVariables().get(0));
            return ModelCreators.of(ModelReference.of(modelPath, managedType), initializer)
                    .withProjection(projection)
                    .descriptor(descriptor)
                    .inputs(inputs)
                    .build();
        } else {
            return ManagedModelInitializer.creator(descriptor, modelPath, modelSchema, schemaStore, modelInstantiator, proxyFactory, inputs, new RuleMethodBackedMutationAction<T>(modelSchema, ruleDefinition.getRuleInvoker(), descriptor, inputs));
        }
    }

    private <T> ModelSchema<T> getModelSchema(ModelType<T> managedType, MethodRuleDefinition<?, ?> ruleDefinition) {
        try {
            return schemaStore.getSchema(managedType);
        } catch (InvalidManagedModelElementTypeException e) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), e);
        }
    }

    // This thing is temporary
    private static class ManagedModelRuleInvokerInstanceBackedTransformer<T> implements BiAction<MutableModelNode, Inputs> {
        private final ModelSchema<T> schema;
        private final ModelInstantiator instantiator;
        private final ModelRuleInvoker<?> ruleInvoker;
        private final ModelRuleDescriptor descriptor;
        private final List<ModelReference<?>> inputReferences;

        public ManagedModelRuleInvokerInstanceBackedTransformer(ModelSchema<T> schema, ModelInstantiator instantiator, ModelRuleInvoker<?> ruleInvoker, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputReferences) {
            this.schema = schema;
            this.instantiator = instantiator;
            this.ruleInvoker = ruleInvoker;
            this.descriptor = descriptor;
            this.inputReferences = inputReferences;
        }

        public void execute(MutableModelNode modelNode, Inputs inputs) {
            T instance = instantiator.newInstance(schema);
            modelNode.setPrivateData(schema.getType(), instance);

            new RuleMethodBackedMutationAction<T>(schema, ruleInvoker, descriptor, inputReferences).execute(modelNode, inputs);
        }
    }
}
