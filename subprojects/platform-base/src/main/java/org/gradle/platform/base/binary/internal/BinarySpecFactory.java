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
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.binary.BaseBinarySpec;

import java.util.Set;

public class BinarySpecFactory extends BaseInstanceFactory<BinarySpec> {
    private final Instantiator instantiator;
    private final ITaskFactory taskFactory;

    public BinarySpecFactory(String displayName, Instantiator instantiator, ITaskFactory taskFactory) {
        super(displayName, BinarySpec.class, BaseBinarySpec.class);
        this.instantiator = instantiator;
        this.taskFactory = taskFactory;
    }

    public <S extends BinarySpec, T extends BaseBinarySpec> void register(final ModelType<S> publicType, final ModelType<T> implementationType,
                                                                                Set<Class<?>> internalViews, final ModelRuleDescriptor descriptor) {
        InstanceFactory.TypeRegistrationBuilder<S> registration = register(publicType, descriptor);
        if (implementationType != null) {
            registration.withImplementation(Cast.<ModelType<? extends S>>uncheckedCast(implementationType), new InstanceFactory.ImplementationFactory<S>() {
                @Override
                public S create(ModelType<? extends S> publicType, String name, MutableModelNode binaryNode) {
                    MutableModelNode componentBinariesNode = binaryNode.getParent();
                    MutableModelNode componentNode = componentBinariesNode.getParent();
                    return Cast.uncheckedCast(BaseBinarySpec.create(
                            publicType.getConcreteClass(),
                            implementationType.getConcreteClass(),
                            name,
                            binaryNode,
                            componentNode,
                            instantiator,
                            taskFactory));
                }
            });
        }
        for (Class<?> internalView : internalViews) {
            registration.withInternalView(ModelType.of(internalView));
        }
    }
}
