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
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ManagedChildNodeCreatorStrategy;
import org.gradle.model.internal.inspect.ProjectionOnlyNodeInitializer;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

public class ManagedSetNodeInitializerExtractionStrategy extends CollectionNodeInitializerExtractionSupport {
    private static final ModelType<ManagedSet<?>> MANAGED_SET_MODEL_TYPE = new ModelType<ManagedSet<?>>() {
    };

    @Override
    protected <T, E> NodeInitializer extractNodeInitializer(ModelCollectionSchema<T, E> schema, NodeInitializerRegistry nodeInitializerRegistry) {
        if (MANAGED_SET_MODEL_TYPE.isAssignableFrom(schema.getType())) {
            ModelProjection projection = TypedModelProjection.of(
                ModelTypes.managedSet(schema.getElementType()),
                new ManagedSetModelViewFactory<E>(schema.getElementType(), nodeInitializerRegistry)
            );
            return new ProjectionOnlyNodeInitializer(projection);
        }
        return null;
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableList.<ModelType<?>>of(MANAGED_SET_MODEL_TYPE);
    }

    private static class ManagedSetModelViewFactory<T> implements ModelViewFactory<ManagedSet<T>> {
        private final ModelType<T> elementType;
        private final NodeInitializerRegistry nodeInitializerRegistry;

        public ManagedSetModelViewFactory(ModelType<T> elementType, NodeInitializerRegistry nodeInitializerRegistry) {
            this.elementType = elementType;
            this.nodeInitializerRegistry = nodeInitializerRegistry;
        }

        @Override
        public ModelView<ManagedSet<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<ManagedSet<T>> setType = ModelTypes.managedSet(elementType);
            DefaultModelViewState state = new DefaultModelViewState(setType, ruleDescriptor, writable, !writable);
            NodeBackedModelSet<T> set = new NodeBackedModelSet<T>(setType.toString() + " '" + modelNode.getPath() + "'", elementType, ruleDescriptor, modelNode, state, new ManagedChildNodeCreatorStrategy<T>(nodeInitializerRegistry));
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

            ManagedSetModelViewFactory<?> that = (ManagedSetModelViewFactory<?>) o;
            return elementType.equals(that.elementType);

        }

        @Override
        public int hashCode() {
            return elementType.hashCode();
        }
    }
}
