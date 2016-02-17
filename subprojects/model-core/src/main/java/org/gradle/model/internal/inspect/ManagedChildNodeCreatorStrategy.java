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

package org.gradle.model.internal.inspect;

import org.gradle.api.specs.Spec;
import org.gradle.model.internal.core.ChildNodeInitializerStrategy;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.type.ModelType;

import static org.gradle.model.internal.core.NodeInitializerContext.forType;

public class ManagedChildNodeCreatorStrategy<T> implements ChildNodeInitializerStrategy<T> {

    private final NodeInitializerRegistry nodeInitializerRegistry;

    public ManagedChildNodeCreatorStrategy(NodeInitializerRegistry nodeInitializerRegistry) {
        this.nodeInitializerRegistry = nodeInitializerRegistry;
    }

    @Override
    public <S extends T> NodeInitializer initializer(ModelType<S> type, Spec<ModelType<?>> constraints) {
        return nodeInitializerRegistry.getNodeInitializer(forType(type));
    }
}
