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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import org.gradle.internal.BiAction;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.CollectionSchema;
import org.gradle.model.internal.type.ModelType;

public class ModelMapNodeInitializerExtractionStrategy extends CollectionNodeInitializerExtractionSupport {
    private static final ModelType<ModelMap<?>> MODEL_MAP_MODEL_TYPE = new ModelType<ModelMap<?>>() {
    };

    @Override
    protected <T, E> NodeInitializer extractNodeInitializer(CollectionSchema<T, E> schema, NodeInitializerContext<T> context) {
        if (MODEL_MAP_MODEL_TYPE.isAssignableFrom(schema.getType())) {
            return new ModelMapNodeInitializer<T, E>(schema);
        }
        return null;
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableList.<ModelType<?>>of(MODEL_MAP_MODEL_TYPE);
    }

    private static class ModelMapNodeInitializer<T, E> implements NodeInitializer {
        private final CollectionSchema<T, E> schema;

        public ModelMapNodeInitializer(CollectionSchema<T, E> schema) {
            this.schema = schema;
        }

        @Override
        public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
            return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
                .put(ModelActionRole.Discover, AddProjectionsAction.of(subject, descriptor,
                    ModelMapModelProjection.managed(schema.getType(), schema.getElementType(), ChildNodeInitializerStrategyAccessors.fromPrivateData())
                ))
                .put(ModelActionRole.Create, DirectNodeInputUsingModelAction.of(subject, descriptor,
                    ModelReference.of(NodeInitializerRegistry.class),
                    new BiAction<MutableModelNode, NodeInitializerRegistry>() {
                        @Override
                        public void execute(MutableModelNode modelNode, NodeInitializerRegistry nodeInitializerRegistry) {
                            ChildNodeInitializerStrategy<E> childStrategy = NodeBackedModelMap.createUsingRegistry(nodeInitializerRegistry);
                            modelNode.setPrivateData(ChildNodeInitializerStrategy.class, childStrategy);
                        }
                    }
                ))
                .build();
        }
    }
}
