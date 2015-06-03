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

package org.gradle.language.base.internal.model;

import org.gradle.api.Action;
import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.ModelCreator;
import org.gradle.model.internal.core.ModelCreators;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.UnmanagedModelProjection;
import org.gradle.model.internal.core.rule.describe.StandardDescriptorFactory;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;

public class ComponentSpecInitializer {

    private final static BiAction<MutableModelNode, BinarySpec> BINARY_ACTION = new BiAction<MutableModelNode, BinarySpec>() {
        @Override
        public void execute(MutableModelNode node, BinarySpec spec) {
            final ModelType<BinaryTasksCollection> itemType = ModelType.of(BinaryTasksCollection.class);
            ModelCreator itemCreator = ModelCreators.of(node.getPath().child("tasks"), new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode modelNode) {
                    BinaryTasksCollection tasks = modelNode.getParent().getPrivateData(ModelType.of(BinarySpec.class)).getTasks();
                    modelNode.setPrivateData(itemType, tasks);
                }
            })
                .withProjection(new UnmanagedModelProjection<BinaryTasksCollection>(itemType))
                .descriptor(new StandardDescriptorFactory(node.getDescriptor()).transform("tasks"))
                .build();
            node.addLink(itemCreator);
        }
    };

    public static BiAction<MutableModelNode, BinarySpec> binaryAction() {
        return BINARY_ACTION;
    }

}
