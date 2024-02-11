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

package org.gradle.model.internal.core;

import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.RuleContext;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

public class ModelMaps {

    private static final ModelReference<NodeInitializerRegistry> NODE_INITIALIZER_REGISTRY_MODEL_REFERENCE = ModelReference.of(NodeInitializerRegistry.class);
    private static final ModelType<ChildNodeInitializerStrategy<?>> CHILD_NODE_INITIALIZER_STRATEGY_MODEL_TYPE =
        Cast.uncheckedCast(ModelType.of(ChildNodeInitializerStrategy.class));

    public static <T> MutableModelNode addModelMapNode(MutableModelNode modelNode, ModelType<T> elementModelType, String name) {
        modelNode.addLink(
            ModelRegistrations.of(modelNode.getPath().child(name))
                .action(ModelActionRole.Create, NODE_INITIALIZER_REGISTRY_MODEL_REFERENCE, new BiAction<MutableModelNode, NodeInitializerRegistry>() {
                    @Override
                    public void execute(MutableModelNode node, NodeInitializerRegistry nodeInitializerRegistry) {
                        ChildNodeInitializerStrategy<T> childFactory =
                            NodeBackedModelMap.createUsingRegistry(nodeInitializerRegistry);
                        node.setPrivateData(CHILD_NODE_INITIALIZER_STRATEGY_MODEL_TYPE, childFactory);
                    }
                })
                .descriptor(modelNode.getDescriptor())
                .withProjection(
                    ModelMapModelProjection.unmanaged(elementModelType, ChildNodeInitializerStrategyAccessors.fromPrivateData())
                )
                .build()
        );
        MutableModelNode mapNode = modelNode.getLink(name);
        assert mapNode != null;
        return mapNode;
    }

    public static <T> ModelMap<T> toView(MutableModelNode mapNode, ModelType<T> elementModelType) {
        mapNode.ensureUsable();
        return mapNode.asMutable(
                ModelTypes.modelMap(elementModelType),
                RuleContext.get()
        ).getInstance();
    }
}
