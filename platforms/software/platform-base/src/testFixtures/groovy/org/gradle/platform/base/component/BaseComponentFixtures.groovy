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

package org.gradle.platform.base.component


import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.component.internal.DefaultComponentSpec
import org.gradle.platform.base.internal.ComponentSpecIdentifier
import org.gradle.platform.base.internal.ComponentSpecInternal
import org.gradle.test.fixtures.BaseInstanceFixtureSupport

class BaseComponentFixtures {
    static <T extends ComponentSpec, I extends BaseComponentSpec> T create(Class<T> publicType, Class<I> implType, ComponentSpecIdentifier componentId) {
        def node = createNode(publicType, implType, componentId);
        return node.asMutable(ModelType.of(publicType), new SimpleModelRuleDescriptor(componentId.getName())).getInstance()
    }

    static <T extends ComponentSpec, I extends BaseComponentSpec> MutableModelNode createNode(Class<T> publicType, Class<I> implType, ComponentSpecIdentifier componentId) {
        BaseInstanceFixtureSupport.createNode(publicType, ComponentSpecInternal, implType, componentId.name) { MutableModelNode node ->
            return DefaultComponentSpec.create(publicType, implType, componentId, node)
        }
    }
}
