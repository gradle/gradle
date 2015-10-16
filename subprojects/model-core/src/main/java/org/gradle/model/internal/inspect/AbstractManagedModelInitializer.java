/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Named;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public abstract class AbstractManagedModelInitializer<T> implements NodeInitializer {

    protected final ModelManagedImplStructSchema<T> schema;
    protected final ModelSchemaStore schemaStore;
    protected final ManagedProxyFactory proxyFactory;

    public AbstractManagedModelInitializer(ModelManagedImplStructSchema<T> schema, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory) {
        this.schema = schema;
        this.schemaStore = schemaStore;
        this.proxyFactory = proxyFactory;
    }

    protected void addPropertyLinks(MutableModelNode modelNode, NodeInitializerRegistry nodeInitializerRegistry, Collection<ModelProperty<?>> properties) {
        for (ModelProperty<?> property : properties) {
            addPropertyLink(modelNode, property, nodeInitializerRegistry);
        }
        if (isANamedType()) {
            // Only initialize "name" child node if the schema has such a managed property.
            // This is not the case for a managed subtype of an unmanaged type that implements Named.
            ModelProperty<?> nameProperty = schema.getProperty("name");
            if (nameProperty != null && nameProperty.getStateManagementType().equals(ModelProperty.StateManagementType.MANAGED)
                && properties.contains(nameProperty)) {
                MutableModelNode nameLink = modelNode.getLink("name");
                if (nameLink == null) {
                    throw new IllegalStateException("expected name node for " + modelNode.getPath());
                }
                nameLink.setPrivateData(ModelType.of(String.class), modelNode.getPath().getName());
            }
        }
    }

    private <P> void addPropertyLink(MutableModelNode modelNode, ModelProperty<P> property, NodeInitializerRegistry nodeInitializerRegistry) {
        // No need to create nodes for unmanaged properties
        if (!property.getStateManagementType().equals(ModelProperty.StateManagementType.MANAGED)) {
            return;
        }
        ModelType<P> propertyType = property.getType();
        ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);

        validateProperty(propertySchema, property, nodeInitializerRegistry);

        ModelRuleDescriptor descriptor = modelNode.getDescriptor();
        ModelPath childPath = modelNode.getPath().child(property.getName());
        if (propertySchema instanceof ManagedImplModelSchema) {
            if (!property.isWritable()) {
                ModelCreator creator = ModelCreators.of(childPath, nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forProperty(propertySchema.getType(), property, schema.getType())))
                    .descriptor(descriptor)
                    .build();
                modelNode.addLink(creator);
            } else {
                if (propertySchema instanceof ScalarCollectionSchema) {
                    ModelCreator creator = ModelCreators.of(childPath, nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forProperty(propertySchema.getType(), property, schema.getType())))
                        .descriptor(descriptor)
                        .build();
                    modelNode.addLink(creator);
                } else {
                    ModelManagedImplStructSchema<P> structSchema = (ModelManagedImplStructSchema<P>) propertySchema;
                    ModelProjection projection = new ManagedModelProjection<P>(structSchema, null, schemaStore, proxyFactory);
                    ModelCreator creator = ModelCreators.of(childPath)
                        .withProjection(projection)
                        .descriptor(descriptor).build();
                    modelNode.addReference(creator);
                }
            }
        } else {
            ModelProjection projection = new UnmanagedModelProjection<P>(propertyType, true, true);
            ModelCreators.Builder creatorBuilder;
            if (shouldHaveANodeInitializer(property, propertySchema)) {
                creatorBuilder = ModelCreators.of(childPath, nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forProperty(propertyType, property, schema.getType())));
            } else {
                creatorBuilder = ModelCreators.of(childPath);
            }
            creatorBuilder
                .withProjection(projection)
                .descriptor(descriptor);
            modelNode.addLink(creatorBuilder.build());
        }
    }

    private <P> void validateProperty(ModelSchema<P> propertySchema, ModelProperty<P> property, NodeInitializerRegistry nodeInitializerRegistry) {
        if (propertySchema instanceof ManagedImplModelSchema) {
            if (!property.isWritable()) {
                if (propertySchema instanceof ModelCollectionSchema && !(propertySchema instanceof ScalarCollectionSchema)) {
                    ModelCollectionSchema<P, ?> propertyCollectionsSchema = (ModelCollectionSchema<P, ?>) propertySchema;
                    ModelType<?> elementType = propertyCollectionsSchema.getElementType();
                    nodeInitializerRegistry.ensureHasInitializer(NodeInitializerContext.forProperty(elementType, property, schema.getType()));
                }
                if (property.isDeclaredAsHavingUnmanagedType()) {
                    throw new UnmanagedPropertyMissingSetterException(property);
                }
            }
        } else if (!shouldHaveANodeInitializer(property, propertySchema) && !property.isWritable() && !isNamePropertyOfANamedType(property)) {
            throw new ReadonlyImmutableManagedPropertyException(schema.getType(), property.getName(), property.getType());
        }
    }

    private <P> boolean isNamePropertyOfANamedType(ModelProperty<P> property) {
        return isANamedType() && "name".equals(property.getName());
    }

    public boolean isANamedType() {
        return Named.class.isAssignableFrom(schema.getType().getRawClass());
    }

    private <P> boolean shouldHaveANodeInitializer(ModelProperty<P> property, ModelSchema<P> propertySchema) {
        return !isAModelValueSchema(propertySchema) && !property.isDeclaredAsHavingUnmanagedType();
    }

    private <P> boolean isAModelValueSchema(ModelSchema<P> propertySchema) {
        return propertySchema instanceof ModelValueSchema;
    }
}
