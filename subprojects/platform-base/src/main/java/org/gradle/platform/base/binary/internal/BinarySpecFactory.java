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

package org.gradle.platform.base.binary.internal;

import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.typeregistration.BaseInstanceFactory;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.binary.BaseBinarySpec;

public class BinarySpecFactory extends BaseInstanceFactory<BinarySpec> {
    public BinarySpecFactory(final Instantiator instantiator, final ITaskFactory taskFactory) {
        super(BinarySpec.class);
        registerFactory(BaseBinarySpec.class, new ImplementationFactory<BinarySpec, BaseBinarySpec>() {
            @Override
            public <T extends BaseBinarySpec> T create(ModelType<? extends BinarySpec> publicType, ModelType<T> implementationType, String name, MutableModelNode binaryNode) {
                MutableModelNode componentBinariesNode = binaryNode.getParent();
                MutableModelNode componentNode = componentBinariesNode.getParent();
                return BaseBinarySpec.create(
                        publicType.getConcreteClass(),
                        implementationType.getConcreteClass(),
                        name,
                        binaryNode,
                        componentNode,
                        instantiator,
                        taskFactory);
            }
        });
    }
}
