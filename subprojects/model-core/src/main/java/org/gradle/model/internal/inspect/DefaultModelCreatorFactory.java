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

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Nullable;
import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.model.ModelSet;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.Collections;
import java.util.List;

public class DefaultModelCreatorFactory implements ModelCreatorFactory {
    private final ModelSchemaStore schemaStore;
    private final ManagedProxyFactory proxyFactory;
    private ManagedChildNodeCreatorStrategy<?> managedChildCreatorStrategy;

    public DefaultModelCreatorFactory(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
        this.proxyFactory = new ManagedProxyFactory();
        this.managedChildCreatorStrategy = new ManagedChildNodeCreatorStrategy<Object>(this, schemaStore);
    }

    @Override
    public <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema) {
        return creator(descriptor, path, schema, (ModelAction<T>) null);
    }

    @Override
    public <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, Action<? super T> initializer) {
        ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        ModelAction<T> modelAction = new NoInputsModelAction<T>(modelReference, descriptor, initializer);
        return creator(descriptor, path, schema, modelAction);
    }

    @Override
    public <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, List<ModelReference<?>> initializerInputs, BiAction<? super T, ? super List<ModelView<?>>> initializer) {
        ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        ModelAction<T> modelAction = new InputUsingModelAction<T>(modelReference, descriptor, initializerInputs, initializer);
        return creator(descriptor, path, schema, modelAction);
    }

    private <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, @Nullable ModelAction<T> initializer) {
        ModelType<T> type = schema.getType();
        ModelCreators.Builder builder;

        if (schema instanceof ModelCollectionSchema) {
            builder = ModelCreators.of(path);
            ModelCollectionSchema<T> collectionSchema = (ModelCollectionSchema<T>) schema;
            ModelType<?> elementType = collectionSchema.getElementType();
            addCollectionProjection(builder, collectionSchema, elementType);
        } else if (schema instanceof ModelStructSchema) {
            ModelStructSchema<T> structSchema = (ModelStructSchema<T>) schema;
            builder = ModelCreators.of(path, new ManagedModelInitializer<T>(descriptor, structSchema, schemaStore, this))
                .withProjection(new ManagedModelProjection<T>(structSchema, schemaStore, proxyFactory));
        } else {
            throw new IllegalArgumentException("Don't know how to create model element from schema for " + type);
        }

        builder.descriptor(descriptor);

        if (schema.getKind() == ModelSchema.Kind.STRUCT && Named.class.isAssignableFrom(type.getRawClass())) {
            builder.action(ModelActionRole.Initialize, new NamedInitializer(path, descriptor));
        }
        if (initializer != null) {
            builder.action(ModelActionRole.Initialize, initializer);
        }

        return builder.build();
    }

    @SuppressWarnings("deprecation")
    private <T, E> void addCollectionProjection(ModelCreators.Builder builder, ModelCollectionSchema<T> collectionSchema, ModelType<E> elementType) {
        if (collectionSchema.isMap()) {
            builder.withProjection(modelMapProjection(elementType));
        } else {
            Class<? super T> setType = collectionSchema.getType().getRawClass();
            if (setType.equals(ModelSet.class)) {
                builder.withProjection(
                    TypedModelProjection.of(ModelTypes.modelSet(elementType), modelSetFactory(elementType))
                );
            } else if (setType.equals(ManagedSet.class)) {
                builder.withProjection(
                    TypedModelProjection.of(ModelTypes.managedSet(elementType), managedSetFactory(elementType))
                );
            } else {
                throw new IllegalStateException("Expected ModelSet or ManagedSet");
            }
        }
    }

    private <T> ModelProjection modelMapProjection(ModelType<T> elementType) {
        return ModelMapModelProjection.managed(elementType, childCreator());
    }

    private <T> ChildNodeCreatorStrategy<T> childCreator() {
        return Cast.uncheckedCast(managedChildCreatorStrategy);
    }

    private <T> ChildNodeCreatorStrategy<T> childCreator(@SuppressWarnings("UnusedParameters") ModelType<T> modelType) {
        return Cast.uncheckedCast(managedChildCreatorStrategy);
    }

    private static class ManagedChildNodeCreatorStrategy<T> implements ChildNodeCreatorStrategy<T> {
        private final ModelCreatorFactory modelCreatorFactory;
        private final ModelSchemaStore modelSchemaStore;

        public ManagedChildNodeCreatorStrategy(ModelCreatorFactory modelCreatorFactory, ModelSchemaStore modelSchemaStore) {
            this.modelCreatorFactory = modelCreatorFactory;
            this.modelSchemaStore = modelSchemaStore;
        }

        @Override
        public <S extends T> ModelCreator creator(MutableModelNode parentNode, ModelRuleDescriptor sourceDescriptor, ModelType<S> type, final String name) {
            ModelPath childPath = parentNode.getPath().child(name);
            return modelCreatorFactory.creator(sourceDescriptor, childPath, modelSchemaStore.getSchema(type));
        }
    }

    private static class NamedInitializer implements ModelAction<Object> {

        private final ModelPath modelPath;
        private final ModelRuleDescriptor parentDescriptor;

        public NamedInitializer(ModelPath modelPath, ModelRuleDescriptor parentDescriptor) {
            this.modelPath = modelPath;
            this.parentDescriptor = parentDescriptor;
        }

        @Override
        public ModelReference<Object> getSubject() {
            return ModelReference.of(modelPath);
        }

        @Override
        public void execute(MutableModelNode modelNode, Object object, List<ModelView<?>> inputs) {
            MutableModelNode nameLink = modelNode.getLink("name");
            if (nameLink == null) {
                throw new IllegalStateException("expected name node for " + modelNode.getPath());
            }
            nameLink.setPrivateData(ModelType.of(String.class), modelNode.getPath().getName());
        }

        @Override
        public List<ModelReference<?>> getInputs() {
            return Collections.emptyList();
        }

        @Override
        public ModelRuleDescriptor getDescriptor() {
            return new NestedModelRuleDescriptor(parentDescriptor, "<set name>");
        }
    }

    @SuppressWarnings("deprecation")
    private <T> ModelViewFactory<ManagedSet<T>> managedSetFactory(final ModelType<T> elementType) {
        return new ManagedSetModelViewFactory<T>(elementType);
    }

    private <T> ModelViewFactory<ModelSet<T>> modelSetFactory(final ModelType<T> elementType) {
        return new ModelSetModelViewFactory<T>(elementType);
    }

    private class ManagedSetModelViewFactory<T> implements ModelViewFactory<ManagedSet<T>> {
        private final ModelType<T> elementType;

        public ManagedSetModelViewFactory(ModelType<T> elementType) {
            this.elementType = elementType;
        }

        @Override
        public ModelView<ManagedSet<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<ManagedSet<T>> setType = ModelTypes.managedSet(elementType);
            DefaultModelViewState state = new DefaultModelViewState(setType, ruleDescriptor, writable, !writable);
            NodeBackedModelSet<T> set = new NodeBackedModelSet<T>(setType.toString() + " '" + modelNode.getPath() + "'", elementType, ruleDescriptor, modelNode, state, childCreator(elementType));
            return new InstanceModelView<ManagedSet<T>>(modelNode.getPath(), setType, set, state.closer());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ManagedSetModelViewFactory<?> that = (ManagedSetModelViewFactory<?>) o;
            return elementType.equals(that.elementType);

        }

        @Override
        public int hashCode() {
            return elementType.hashCode();
        }
    }

    private class ModelSetModelViewFactory<T> implements ModelViewFactory<ModelSet<T>> {
        private final ModelType<T> elementType;

        public ModelSetModelViewFactory(ModelType<T> elementType) {
            this.elementType = elementType;
        }

        @Override
        public ModelView<ModelSet<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<ModelSet<T>> setType = ModelTypes.modelSet(elementType);
            DefaultModelViewState state = new DefaultModelViewState(setType, ruleDescriptor, writable, !writable);
            NodeBackedModelSet<T> set = new NodeBackedModelSet<T>(setType.toString() + " '" + modelNode.getPath() + "'", elementType, ruleDescriptor, modelNode, state, childCreator(elementType));
            return new InstanceModelView<ModelSet<T>>(modelNode.getPath(), setType, set, state.closer());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ModelSetModelViewFactory<?> that = (ModelSetModelViewFactory<?>) o;
            return elementType.equals(that.elementType);

        }

        @Override
        public int hashCode() {
            return elementType.hashCode();
        }
    }
}
