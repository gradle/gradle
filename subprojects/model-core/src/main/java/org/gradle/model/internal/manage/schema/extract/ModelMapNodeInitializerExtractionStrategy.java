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
import org.gradle.model.ModelMap;
import org.gradle.model.collection.internal.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ManagedChildNodeCreatorStrategy;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class ModelMapNodeInitializerExtractionStrategy extends CollectionNodeInitializerExtractionSupport {
    private static final ModelType<ModelMap<?>> MODEL_MAP_MODEL_TYPE = new ModelType<ModelMap<?>>() {
    };
    private final NodeInitializerRegistry nodeInitializerRegistry;

    public ModelMapNodeInitializerExtractionStrategy(NodeInitializerRegistry nodeInitializerRegistry) {
        this.nodeInitializerRegistry = nodeInitializerRegistry;
    }

    @Override
    protected <T, E> NodeInitializer extractNodeInitializer(ModelCollectionSchema<T, E> schema) {
        if (MODEL_MAP_MODEL_TYPE.isAssignableFrom(schema.getType())) {
            if (!nodeInitializerRegistry.hasNodeInitializer(schema.getElementType())) {
                return null;
            }
            return new ModelMapNodeInitializer<T, E>(schema);
        }
        return null;
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableList.<ModelType<?>>of(MODEL_MAP_MODEL_TYPE);
    }

    private static class ModelMapNodeInitializer<T, E> implements NodeInitializer {
        private final ModelCollectionSchema<T, E> schema;

        public ModelMapNodeInitializer(ModelCollectionSchema<T, E> schema) {
            this.schema = schema;
        }

        @Override
        public List<? extends ModelReference<?>> getInputs() {
            return Collections.singletonList(ModelReference.of(NodeInitializerRegistry.class));
        }

        @Override
        public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {

            NodeInitializerRegistry nodeInitializerRegistry = ModelViews.assertType(inputs.get(0), NodeInitializerRegistry.class).getInstance();

            ManagedChildNodeCreatorStrategy<E> childCreator = new ManagedChildNodeCreatorStrategy<E>(nodeInitializerRegistry);
            modelNode.setPrivateData(ChildNodeInitializerStrategy.class, childCreator);
        }

        @Override
        public List<? extends ModelProjection> getProjections() {
            return Collections.singletonList(
                ModelMapModelProjection.managed(schema.getElementType(), ChildNodeInitializerStrategyAccessors.fromPrivateData())
            );
        }

        @Nullable
        @Override
        public ModelAction getProjector(ModelPath path, ModelRuleDescriptor descriptor) {
            return null;
        }
    }
}
