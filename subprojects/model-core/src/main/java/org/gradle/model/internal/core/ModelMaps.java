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
import org.gradle.model.collection.internal.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.internal.registry.RuleContext;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.List;

public class ModelMaps {
    public static <T> MutableModelNode addModelMapNode(MutableModelNode modelNode, Class<T> elementType, String name) {
        final ModelType<T> elementModelType = ModelType.of(elementType);
        modelNode.addLink(
            ModelRegistrations.of(
                modelNode.getPath().child(name), ModelReference.of(NodeInitializerRegistry.class), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode node, List<ModelView<?>> modelViews) {
                        NodeInitializerRegistry nodeInitializerRegistry = (NodeInitializerRegistry) modelViews.get(0).getInstance();
                        ChildNodeInitializerStrategy<T> childFactory =
                            NodeBackedModelMap.createUsingRegistry(elementModelType, nodeInitializerRegistry);
                        node.setPrivateData(ModelType.of(ChildNodeInitializerStrategy.class), childFactory);
                    }
                })
                .descriptor(modelNode.getDescriptor(), "." + name)
                .withProjection(
                    ModelMapModelProjection.unmanaged(elementModelType, ChildNodeInitializerStrategyAccessors.fromPrivateData())
                )
                .build()
        );
        MutableModelNode mapNode = modelNode.getLink(name);
        assert mapNode != null;
        return mapNode;
    }

    public static <T> ModelMap<T> asMutableView(MutableModelNode mapNode, Class<T> elementType, String ruleContext) {
        final ModelType<T> elementModelType = ModelType.of(elementType);
        mapNode.ensureUsable();
        return mapNode.asMutable(
                ModelTypes.modelMap(elementModelType),
                RuleContext.nest(ruleContext)
        ).getInstance();

    }
}
