/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Named;
import org.gradle.internal.BiAction;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.binding.ManagedProperty;
import org.gradle.model.internal.manage.binding.StructBindings;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.List;

import static org.gradle.model.internal.core.ModelViews.getInstance;
import static org.gradle.model.internal.core.NodeInitializerContext.forProperty;

public class StructNodeInitializer<T> implements NodeInitializer {

    protected final StructBindings<T> bindings;

    public StructNodeInitializer(StructBindings<T> bindings) {
        this.bindings = bindings;
    }

    @Override
    public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
        return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
            .put(ModelActionRole.Discover, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(ManagedProxyFactory.class),
                    ModelReference.of(TypeConverter.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        ManagedProxyFactory proxyFactory = getInstance(modelViews.get(0), ManagedProxyFactory.class);
                        TypeConverter typeConverter = getInstance(modelViews, 1, TypeConverter.class);
                        for (StructSchema<?> viewSchema : bindings.getDeclaredViewSchemas()) {
                            addProjection(modelNode, viewSchema, proxyFactory, typeConverter);
                        }
                        modelNode.addProjection(new ModelElementProjection(bindings.getPublicSchema().getType()));
                    }
                }
            ))
            .put(ModelActionRole.Create, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(ModelSchemaStore.class),
                    ModelReference.of(NodeInitializerRegistry.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        ModelSchemaStore schemaStore = getInstance(modelViews, 0, ModelSchemaStore.class);
                        NodeInitializerRegistry nodeInitializerRegistry = getInstance(modelViews, 1, NodeInitializerRegistry.class);

                        addPropertyLinks(modelNode, schemaStore, nodeInitializerRegistry);
                        initializePrivateData(modelNode);
                    }
                }
            ))
            .build();
    }

    protected void initializePrivateData(MutableModelNode modelNode) {
    }

    private <V> void addProjection(MutableModelNode modelNode, StructSchema<V> viewSchema, ManagedProxyFactory proxyFactory, TypeConverter typeConverter) {
        modelNode.addProjection(new ManagedModelProjection<V>(viewSchema, bindings, proxyFactory, typeConverter));
    }

    private void addPropertyLinks(MutableModelNode modelNode,
                                  ModelSchemaStore schemaStore,
                                  NodeInitializerRegistry nodeInitializerRegistry
    ) {
        for (ManagedProperty<?> property : bindings.getManagedProperties().values()) {
            addPropertyLink(modelNode, property, schemaStore, nodeInitializerRegistry);
        }
        if (isNamedType()) {
            // Only initialize "name" child node if the schema has such a managed property.
            // This is not the case for a managed subtype of an unmanaged type that implements Named.
            if (bindings.getManagedProperties().containsKey("name")) {
                MutableModelNode nameLink = modelNode.getLink("name");
                if (nameLink == null) {
                    throw new IllegalStateException("expected name node for " + modelNode.getPath());
                }
                nameLink.setPrivateData(ModelType.of(String.class), modelNode.getPath().getName());
            }
        }
    }

    private <P> void addPropertyLink(MutableModelNode modelNode,
                                     ManagedProperty<P> property,
                                     ModelSchemaStore schemaStore,
                                     NodeInitializerRegistry nodeInitializerRegistry
    ) {
        ModelType<P> propertyType = property.getType();
        ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);
        ModelType<T> publicType = bindings.getPublicSchema().getType();

        validateProperty(propertySchema, property, nodeInitializerRegistry);

        ModelPath childPath = modelNode.getPath().child(property.getName());
        if (propertySchema instanceof ManagedImplSchema) {
            if (!property.isWritable() || propertySchema instanceof ScalarCollectionSchema) {
                ModelRegistrations.Builder builder = managedRegistrationBuilder(childPath, property, nodeInitializerRegistry, publicType);
                addLink(modelNode, builder, property.isInternal());
            } else {
                // A nullable reference
                modelNode.addReference(property.getName(), propertyType, null, modelNode.getDescriptor());
            }
        } else {
            ModelRegistrations.Builder registrationBuilder;
            if (shouldHaveANodeInitializer(property, propertySchema)) {
                registrationBuilder = managedRegistrationBuilder(childPath, property, nodeInitializerRegistry, publicType);
            } else {
                registrationBuilder = ModelRegistrations.of(childPath);
            }
            registrationBuilder.withProjection(new UnmanagedModelProjection<P>(propertyType));
            registrationBuilder.withProjection(new ModelElementProjection(propertyType));
            addLink(modelNode, registrationBuilder, property.isInternal());
        }
    }

    private static <P> ModelRegistrations.Builder managedRegistrationBuilder(ModelPath childPath, ManagedProperty<P> property, NodeInitializerRegistry nodeInitializerRegistry, ModelType<?> publicType) {
        return ModelRegistrations.of(childPath, nodeInitializerRegistry.getNodeInitializer(forProperty(property.getType(), property, publicType)));
    }

    private void addLink(MutableModelNode modelNode, ModelRegistrations.Builder builder, boolean internal) {
        ModelRegistration registration = builder
            .descriptor(modelNode.getDescriptor())
            .hidden(internal)
            .build();
        modelNode.addLink(registration);
    }

    private <P> void validateProperty(ModelSchema<P> propertySchema, ManagedProperty<P> property, NodeInitializerRegistry nodeInitializerRegistry) {
        if (propertySchema instanceof ManagedImplSchema) {
            if (!property.isWritable()) {
                if (isCollectionOfManagedTypes(propertySchema)) {
                    CollectionSchema<P, ?> propertyCollectionsSchema = (CollectionSchema<P, ?>) propertySchema;
                    ModelType<?> elementType = propertyCollectionsSchema.getElementType();
                    nodeInitializerRegistry.ensureHasInitializer(forProperty(elementType, property, bindings.getPublicSchema().getType()));
                }
                if (property.isDeclaredAsHavingUnmanagedType()) {
                    throw new UnmanagedPropertyMissingSetterException(property.getName());
                }
            }
        } else if (!shouldHaveANodeInitializer(property, propertySchema) && !property.isWritable() && !isNamePropertyOfANamedType(property)) {
            throw new ReadonlyImmutableManagedPropertyException(bindings.getPublicSchema().getType(), property.getName(), property.getType());
        }
    }

    private <P> boolean isCollectionOfManagedTypes(ModelSchema<P> propertySchema) {
        return propertySchema instanceof CollectionSchema
                && !(propertySchema instanceof ScalarCollectionSchema);
    }

    private <P> boolean isNamePropertyOfANamedType(ManagedProperty<P> property) {
        return isNamedType() && "name".equals(property.getName());
    }

    private boolean isNamedType() {
        return Named.class.isAssignableFrom(bindings.getPublicSchema().getType().getRawClass());
    }

    private <P> boolean shouldHaveANodeInitializer(ManagedProperty<P> property, ModelSchema<P> propertySchema) {
        return !(propertySchema instanceof ScalarValueSchema) && !property.isDeclaredAsHavingUnmanagedType();
    }
}
