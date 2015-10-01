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
import org.gradle.model.ModelSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ManagedChildNodeCreatorStrategy;
import org.gradle.model.internal.inspect.ProjectionOnlyNodeInitializer;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

public class ModelSetNodeInitializerExtractionStrategy extends CollectionNodeInitializerExtractionSupport {
    private static final ModelType<ModelSet<?>> MODEL_SET_MODEL_TYPE = new ModelType<ModelSet<?>>() {
    };

    @Override
    protected <T, E> NodeInitializer extractNodeInitializer(ModelCollectionSchema<T, E> schema, NodeInitializerRegistry nodeInitializerRegistry) {
        if (MODEL_SET_MODEL_TYPE.isAssignableFrom(schema.getType())) {
            ModelProjection projection = TypedModelProjection.of(
                ModelTypes.modelSet(schema.getElementType()),
                new ModelSetModelViewFactory<E>(schema.getElementType(), nodeInitializerRegistry)
            );
            return new ProjectionOnlyNodeInitializer(projection);
        }
        return null;
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableList.<ModelType<?>>of(MODEL_SET_MODEL_TYPE);
    }

    private static class ModelSetModelViewFactory<T> implements ModelViewFactory<ModelSet<T>> {
        private final ModelType<T> elementType;
        private final NodeInitializerRegistry nodeInitializerRegistry;

        public ModelSetModelViewFactory(ModelType<T> elementType, NodeInitializerRegistry nodeInitializerRegistry) {
            this.elementType = elementType;
            this.nodeInitializerRegistry = nodeInitializerRegistry;
        }

        @Override
        public ModelView<ModelSet<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<ModelSet<T>> setType = ModelTypes.modelSet(elementType);
            DefaultModelViewState state = new DefaultModelViewState(setType, ruleDescriptor, writable, !writable);
            final ManagedChildNodeCreatorStrategy<T> childCreator = new ManagedChildNodeCreatorStrategy<T>(nodeInitializerRegistry);
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
}
