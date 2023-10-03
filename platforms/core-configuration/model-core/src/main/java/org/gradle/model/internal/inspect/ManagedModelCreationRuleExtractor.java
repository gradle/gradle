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

import org.gradle.internal.BiAction;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.SpecializedMapSchema;
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.extract.SpecializedMapNodeInitializer;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.gradle.model.internal.core.NodeInitializerContext.forType;

public class ManagedModelCreationRuleExtractor extends AbstractModelCreationRuleExtractor {
    private static final ModelType<NodeInitializerRegistry> NODE_INITIALIZER_REGISTRY = ModelType.of(NodeInitializerRegistry.class);

    private final ModelSchemaStore schemaStore;

    public ManagedModelCreationRuleExtractor(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }

    @Override
    public String getDescription() {
        return String.format("%s and taking a managed model element", super.getDescription());
    }

    @Override
    public boolean isSatisfiedBy(MethodRuleDefinition<?, ?> element) {
        return super.isSatisfiedBy(element) && isVoidMethod(element);
    }

    @Override
    protected <R, S> void validateMethod(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        if (ruleDefinition.getReferences().isEmpty()) {
            context.add(ruleDefinition, "A method annotated with @Model must either take at least one parameter or have a non-void return type");
        }
    }

    @Override
    protected <R, S> ExtractedModelRule buildRule(ModelPath modelPath, MethodRuleDefinition<R, S> ruleDefinition) {
        ModelType<S> modelType = ruleDefinition.getSubjectReference().getType();
        final ModelSchema<S> modelSchema = getModelSchema(modelType, ruleDefinition);
        return new ExtractedManagedCreationRule<R, S>(modelPath, ruleDefinition, modelSchema);
    }

    private static NodeInitializer getNodeInitializer(ModelRuleDescriptor descriptor, ModelSchema<?> modelSchema, NodeInitializerRegistry nodeInitializerRegistry) {
        try {
            return nodeInitializerRegistry.getNodeInitializer(forType(modelSchema.getType()));
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

    private static class ExtractedManagedCreationRule<R, S> extends ExtractedCreationRule<R, S> {
        private final ModelSchema<S> modelSchema;

        public ExtractedManagedCreationRule(ModelPath modelPath, MethodRuleDefinition<R, S> ruleDefinition, ModelSchema<S> modelSchema) {
            super(modelPath, ruleDefinition);
            this.modelSchema = modelSchema;
        }

        @Override
        protected void buildRegistration(MethodModelRuleApplicationContext context, ModelRegistrations.Builder registration) {
            MethodRuleDefinition<R, S> ruleDefinition = getRuleDefinition();
            List<ModelReference<?>> bindings = ruleDefinition.getReferences();
            List<ModelReference<?>> inputs = bindings.subList(1, bindings.size());
            final ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();

            if (modelSchema instanceof SpecializedMapSchema) {
                registration.actions(SpecializedMapNodeInitializer.getActions(ModelReference.of(modelPath), descriptor, (SpecializedMapSchema<S, ?>) modelSchema));
            } else {
                registration.action(ModelActionRole.Discover, Collections.singletonList(ModelReference.of(NODE_INITIALIZER_REGISTRY)), new BiAction<MutableModelNode, List<ModelView<?>>>() {
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

            registration.action(ModelActionRole.Initialize,
                    context.contextualize(new MethodBackedModelAction<S>(descriptor, ModelReference.of(modelPath, modelSchema.getType()), inputs)));
        }

    }
}
