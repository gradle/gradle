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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.ModelSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ManagedChildNodeCreatorStrategy;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.Collections;
import java.util.List;

public class ModelSetNodeInitializerExtractionStrategy extends CollectionNodeInitializerExtractionSupport {
    private static final ModelType<ModelSet<?>> MODEL_SET_MODEL_TYPE = new ModelType<ModelSet<?>>() {
    };

    @Override
    protected <T, E> NodeInitializer extractNodeInitializer(ModelCollectionSchema<T, E> schema) {
        if (MODEL_SET_MODEL_TYPE.isAssignableFrom(schema.getType())) {
            return new ModelSetNodeInitializer<T, E>(schema);
        }
        return null;
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableList.<ModelType<?>>of(MODEL_SET_MODEL_TYPE);
    }

    private static class ModelSetModelViewFactory<T> implements ModelViewFactory<ModelSet<T>> {
        private final ModelType<T> elementType;

        public ModelSetModelViewFactory(ModelType<T> elementType) {
            this.elementType = elementType;
        }

        @Override
        public ModelView<ModelSet<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ChildNodeInitializerStrategy<T> childCreator = Cast.uncheckedCast(modelNode.getPrivateData(ChildNodeInitializerStrategy.class));
            ModelType<ModelSet<T>> setType = ModelTypes.modelSet(elementType);
            DefaultModelViewState state = new DefaultModelViewState(setType, ruleDescriptor, writable, !writable);
            NodeBackedModelSet<T> set = new NodeBackedModelSet<T>(setType.toString() + " '" + modelNode.getPath() + "'", elementType, ruleDescriptor, modelNode, state, childCreator);
            return InstanceModelView.of(modelNode.getPath(), setType, set, state.closer());
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

    private static class ModelSetNodeInitializer<T, E> implements NodeInitializer {
        private final ModelCollectionSchema<T, E> schema;

        public ModelSetNodeInitializer(ModelCollectionSchema<T, E> schema) {
            this.schema = schema;
        }

        @Override
        public List<? extends ModelReference<?>> getInputs() {
            return Collections.singletonList(ModelReference.of(NodeInitializerRegistry.class));
        }

        @Override
        public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
            NodeInitializerRegistry nodeInitializerRegistry = ModelViews.assertType(inputs.get(0), NodeInitializerRegistry.class).getInstance();
            ChildNodeInitializerStrategy<T> childCreator = new ManagedChildNodeCreatorStrategy<T>(nodeInitializerRegistry);
            modelNode.setPrivateData(ChildNodeInitializerStrategy.class, childCreator);
        }

        @Override
        public List<? extends ModelProjection> getProjections() {
            return Collections.singletonList(
                TypedModelProjection.of(
                    ModelTypes.modelSet(schema.getElementType()),
                    new ModelSetModelViewFactory<E>(schema.getElementType())
                )
            );
        }

        @Nullable
        @Override
        public ModelAction getProjector(ModelPath path, ModelRuleDescriptor descriptor) {
            return null;
        }
    }
}
