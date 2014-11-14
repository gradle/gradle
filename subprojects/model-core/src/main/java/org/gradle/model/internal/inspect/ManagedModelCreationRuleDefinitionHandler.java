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
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelInstantiator;
import org.gradle.model.internal.manage.instance.strategy.StrategyBackedModelInstantiator;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@NotThreadSafe
public class ManagedModelCreationRuleDefinitionHandler extends AbstractModelCreationRuleDefinitionHandler {

    private final ModelSchemaStore schemaStore = new DefaultModelSchemaStore();
    private final ManagedProxyFactory proxyFactory = new ManagedProxyFactory();
    private final ModelInstantiator modelInstantiator = new StrategyBackedModelInstantiator(schemaStore, proxyFactory);

    public String getDescription() {
        return String.format("@%s and taking a managed model element", super.getDescription());
    }

    @Override
    public Spec<MethodRuleDefinition<?>> getSpec() {
        final Spec<MethodRuleDefinition<?>> superSpec = super.getSpec();
        return new Spec<MethodRuleDefinition<?>>() {
            public boolean isSatisfiedBy(MethodRuleDefinition<?> element) {
                return superSpec.isSatisfiedBy(element) && element.getReturnType().equals(ModelType.of(Void.TYPE));
            }
        };
    }

    public <T> void register(MethodRuleDefinition<T> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        String modelName = determineModelName(ruleDefinition);

        List<ModelReference<?>> references = ruleDefinition.getReferences();
        if (references.isEmpty()) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule has to take a managed model element instance as the first argument");
        }

        ModelType<?> managedType = references.get(0).getType();
        modelRegistry.create(buildModelCreatorForManagedType(managedType, ruleDefinition, ModelPath.path(modelName)));
    }

    private <T> ModelCreator buildModelCreatorForManagedType(ModelType<T> managedType, MethodRuleDefinition<?> ruleDefinition, ModelPath modelPath) {
        ModelSchema<T> modelSchema = getModelSchema(managedType, ruleDefinition);

        if (modelSchema.getKind().equals(ModelSchema.Kind.VALUE)) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), "a void returning model element creation rule cannot take a value type as the first parameter, which is the element being created. Return the value from the method.");
        }

        if (modelSchema.getKind() == ModelSchema.Kind.UNMANAGED) {
            String description = String.format("a void returning model element creation rule has to take an instance of a managed type as the first argument");
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), description);
        }

        List<ModelReference<?>> bindings = ruleDefinition.getReferences();
        List<ModelReference<?>> inputs = bindings.subList(1, bindings.size());
        ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();


        Transformer<Action<ModelNode>, Inputs> transformer;
        ModelProjection projection;

        if (managedType.getConcreteClass().equals(ManagedSet.class)) {
            transformer = new ManagedModelRuleInvokerInstanceBackedTransformer<T>(modelSchema, modelInstantiator, ruleDefinition.getRuleInvoker(), descriptor, inputs);
            projection = new UnmanagedModelProjection<T>(managedType, true, true);

        } else {
            transformer = new ManagedModelRuleInvokerGraphBackedTransformer<T>(modelSchema, schemaStore, modelInstantiator, descriptor, ruleDefinition.getRuleInvoker(), inputs);
            projection = new ManagedModelProjection<T>(managedType, schemaStore, proxyFactory);
        }

        return ModelCreators.of(ModelReference.of(modelPath, managedType), transformer)
                .withProjection(projection)
                .descriptor(descriptor)
                .inputs(inputs)
                .build();
    }

    private <T> ModelSchema<T> getModelSchema(ModelType<T> managedType, MethodRuleDefinition<?> ruleDefinition) {
        try {
            return schemaStore.getSchema(managedType);
        } catch (InvalidManagedModelElementTypeException e) {
            throw new InvalidModelRuleDeclarationException(ruleDefinition.getDescriptor(), e);
        }
    }

    private static class ManagedModelRuleInvokerInstanceBackedTransformer<T> implements Transformer<Action<ModelNode>, Inputs> {
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

        public Action<ModelNode> transform(final Inputs inputs) {
            return new Action<ModelNode>() {
                public void execute(ModelNode modelNode) {
                    T instance = instantiator.newInstance(schema);
                    modelNode.setPrivateData(schema.getType(), instance);

                    ModelView<? extends T> modelView = modelNode.getAdapter().asWritable(schema.getType(), descriptor, inputs, modelNode);
                    if (modelView == null) {
                        throw new IllegalStateException("Couldn't produce managed node as schema type");
                    }

                    Object[] args = new Object[inputs.size() + 1];
                    args[0] = instance;
                    for (int i = 0; i < inputs.size(); i++) {
                        args[i + 1] = inputs.get(i, inputReferences.get(i).getType()).getInstance();
                    }
                    ruleInvoker.invoke(args);
                    modelView.close();
                }
            };
        }
    }

    private static class ManagedModelRuleInvokerGraphBackedTransformer<T> implements Transformer<Action<ModelNode>, Inputs> {

        private final ModelSchema<T> modelSchema;
        private final ModelSchemaStore schemaStore;
        private final ModelRuleInvoker<?> ruleInvoker;
        private final List<ModelReference<?>> inputReferences;
        private final ModelInstantiator modelInstantiator;
        private final ModelRuleDescriptor descriptor;

        private ManagedModelRuleInvokerGraphBackedTransformer(ModelSchema<T> modelSchema, ModelSchemaStore schemaStore, ModelInstantiator modelInstantiator, ModelRuleDescriptor descriptor, ModelRuleInvoker<?> ruleInvoker, List<ModelReference<?>> inputReferences) {
            this.schemaStore = schemaStore;
            this.modelInstantiator = modelInstantiator;
            this.descriptor = descriptor;
            this.ruleInvoker = ruleInvoker;
            this.inputReferences = inputReferences;
            this.modelSchema = modelSchema;
        }

        public Action<ModelNode> transform(final Inputs inputs) {
            return new Action<ModelNode>() {
                public void execute(ModelNode modelNode) {
                    ModelView<? extends T> modelView = modelNode.getAdapter().asWritable(modelSchema.getType(), descriptor, inputs, modelNode);
                    if (modelView == null) {
                        throw new IllegalStateException("Couldn't produce managed node as schema type");
                    }

                    for (ModelProperty<?> property : modelSchema.getProperties().values()) {
                        addPropertyLink(modelNode, property);
                    }

                    T instance = modelView.getInstance();
                    Object[] args = new Object[inputs.size() + 1];
                    args[0] = instance;
                    for (int i = 0; i < inputs.size(); i++) {
                        args[i + 1] = inputs.get(i, inputReferences.get(i).getType()).getInstance();
                    }
                    ruleInvoker.invoke(args);

                    modelView.close();
                }

                private <P> void addPropertyLink(ModelNode modelNode, ModelProperty<P> property) {
                    // TODO reuse pooled projections/promises/adapters
                    ModelType<P> propertyType = property.getType();
                    Set<ModelProjection> projections = Collections.<ModelProjection>singleton(new UnmanagedModelProjection<P>(propertyType, true, true));
                    ModelPromise promise = new ProjectionBackedModelPromise(projections);
                    ModelAdapter adapter = new ProjectionBackedModelAdapter(projections);
                    ModelNode childNode = modelNode.addLink(property.getName(), descriptor, promise, adapter);

                    ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);
                    if (propertySchema.getKind() == ModelSchema.Kind.STRUCT && !property.isWritable()) {
                        P instance = modelInstantiator.newInstance(propertySchema);
                        childNode.setPrivateData(propertyType, instance);
                    }
                }
            };
        }
    }
}
