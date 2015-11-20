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
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public abstract class AbstractManagedModelInitializer<T> implements NodeInitializer {

    protected final StructSchema<T> schema;

    public AbstractManagedModelInitializer(StructSchema<T> schema) {
        this.schema = schema;
    }

    protected void addPropertyLinks(MutableModelNode modelNode,
                                    NodeInitializerRegistry nodeInitializerRegistry,
                                    ManagedProxyFactory proxyFactory,
                                    Collection<ModelProperty<?>> properties,
                                    TypeConverter typeConverter) {
        for (ModelProperty<?> property : properties) {
            addPropertyLink(modelNode, property, nodeInitializerRegistry, proxyFactory, typeConverter);
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

    private <P> void addPropertyLink(MutableModelNode modelNode,
                                     ModelProperty<P> property,
                                     NodeInitializerRegistry nodeInitializerRegistry,
                                     ManagedProxyFactory proxyFactory,
                                     TypeConverter typeConverter) {
        // No need to create nodes for unmanaged properties
        if (!property.getStateManagementType().equals(ModelProperty.StateManagementType.MANAGED)) {
            return;
        }
        ModelType<P> propertyType = property.getType();
        ModelSchema<P> propertySchema = property.getSchema();

        validateProperty(propertySchema, property, nodeInitializerRegistry);

        ModelRuleDescriptor descriptor = modelNode.getDescriptor();
        ModelPath childPath = modelNode.getPath().child(property.getName());
        if (propertySchema instanceof ManagedImplSchema) {
            if (!property.isWritable()) {
                ModelRegistration registration = ModelRegistrations.of(childPath, nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forProperty(propertySchema.getType(), property, schema.getType())))
                    .descriptor(descriptor)
                    .build();
                modelNode.addLink(registration);
            } else {
                if (propertySchema instanceof ScalarCollectionSchema) {
                    ModelRegistration registration = ModelRegistrations.of(childPath, nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forProperty(propertySchema.getType(), property, schema.getType())))
                        .descriptor(descriptor)
                        .build();
                    modelNode.addLink(registration);
                } else {
                    // TODO:LPTR Remove projection for reference node
                    // This shouldn't be needed, but if there's no actual value referenced, model report can only
                    // show the type of the node if we do this for now. It should use the schema instead to find
                    // the type of the property node instead.
                    ManagedImplStructSchema<P> structSchema = (ManagedImplStructSchema<P>) propertySchema;
                    ModelProjection projection = new ManagedModelProjection<P>(structSchema, null, proxyFactory, typeConverter);
                    ModelRegistration registration = ModelRegistrations.of(childPath)
                        .withProjection(projection)
                        .descriptor(descriptor).build();
                    modelNode.addReference(registration);
                }
            }
        } else {
            ModelProjection projection = new UnmanagedModelProjection<P>(propertyType, true, true);
            ModelRegistrations.Builder registrationBuilder;
            if (shouldHaveANodeInitializer(property, propertySchema)) {
                registrationBuilder = ModelRegistrations.of(childPath, nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forProperty(propertyType, property, schema.getType())));
            } else {
                registrationBuilder = ModelRegistrations.of(childPath);
            }
            registrationBuilder
                .withProjection(projection)
                .descriptor(descriptor);
            modelNode.addLink(registrationBuilder.build());
        }
    }

    private <P> void validateProperty(ModelSchema<P> propertySchema, ModelProperty<P> property, NodeInitializerRegistry nodeInitializerRegistry) {
        if (propertySchema instanceof ManagedImplSchema) {
            if (!property.isWritable()) {
                if (propertySchema instanceof CollectionSchema
                        && !(propertySchema instanceof ScalarCollectionSchema)
                        && !(propertySchema instanceof SpecializedMapSchema)) {
                    CollectionSchema<P, ?> propertyCollectionsSchema = (CollectionSchema<P, ?>) propertySchema;
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
        return !(propertySchema instanceof ScalarValueSchema) && !property.isDeclaredAsHavingUnmanagedType();
    }
}
