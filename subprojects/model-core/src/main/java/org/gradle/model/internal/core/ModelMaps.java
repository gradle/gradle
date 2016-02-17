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
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.RuleContext;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

public class ModelMaps {
    public static <T> MutableModelNode addModelMapNode(MutableModelNode modelNode, Class<T> elementType, String name) {
        final ModelType<T> elementModelType = ModelType.of(elementType);
        modelNode.addLink(
            ModelRegistrations.of(modelNode.getPath().child(name))
                .action(ModelActionRole.Create, ModelReference.of(NodeInitializerRegistry.class), new BiAction<MutableModelNode, NodeInitializerRegistry>() {
                    @Override
                    public void execute(MutableModelNode node, NodeInitializerRegistry nodeInitializerRegistry) {
                        ChildNodeInitializerStrategy<T> childFactory =
                            NodeBackedModelMap.createUsingRegistry(nodeInitializerRegistry);
                        node.setPrivateData(ModelType.of(ChildNodeInitializerStrategy.class), childFactory);
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

    public static <T> ModelMap<T> toView(MutableModelNode mapNode, Class<T> elementType) {
        final ModelType<T> elementModelType = ModelType.of(elementType);
        mapNode.ensureUsable();
        if (mapNode.isMutable()) {
            return mapNode.asMutable(
                    ModelTypes.modelMap(elementModelType),
                    RuleContext.get()
            ).getInstance();
        } else {
            return mapNode.asImmutable(
                    ModelTypes.modelMap(elementModelType),
                    RuleContext.get()
            ).getInstance();
        }
    }
}
