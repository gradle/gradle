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

import org.gradle.api.Action
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.TestNodeInitializerRegistry
import org.gradle.model.internal.manage.instance.ManagedProxyFactory
import org.gradle.model.internal.manage.projection.ManagedModelProjection
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.ComponentSpecIdentifier
import org.gradle.platform.base.internal.ComponentSpecInternal

class BaseComponentFixtures {
    static final def GENERATOR = new AsmBackedClassGenerator()

    static <T extends ComponentSpec, I extends BaseComponentSpec> T create(Class<T> publicType, Class<I> implType, ModelRegistry modelRegistry, ComponentSpecIdentifier componentId) {
        def descriptor = new SimpleModelRuleDescriptor("<create $componentId.name>")
        def node = createNode(publicType, implType,  modelRegistry, componentId, descriptor);
        return node.asMutable(ModelType.of(publicType), new SimpleModelRuleDescriptor(componentId.getName())).getInstance()
    }

    static <T extends ComponentSpec, I extends BaseComponentSpec> MutableModelNode createNode(Class<T> publicType, Class<I> implType, ModelRegistry modelRegistry, ComponentSpecIdentifier componentId) {
        return createNode(publicType, implType, modelRegistry, componentId, new SimpleModelRuleDescriptor("<create $componentId.name>"))
    }

    static <T extends ComponentSpec, I extends BaseComponentSpec> MutableModelNode createNode(Class<T> publicType, Class<I> implType, ModelRegistry modelRegistry, ComponentSpecIdentifier componentId,
                                                                     ModelRuleDescriptor descriptor) {
        modelRegistry.registerInstance("TestNodeInitializerRegistry", TestNodeInitializerRegistry.INSTANCE)

        def viewSchema = DefaultModelSchemaStore.instance.getSchema(publicType)
        def componentSpecInternalSchema = DefaultModelSchemaStore.instance.getSchema(ComponentSpecInternal)
        def delegateSchema = DefaultModelSchemaStore.instance.getSchema(implType)

        def registration = ModelRegistrations.of(ModelPath.path(componentId.name), (Action) { MutableModelNode node ->
                def decorated = GENERATOR.generate(implType)
                def privateData = BaseComponentSpec.create(publicType, decorated, componentId, node)
                node.setPrivateData(implType, privateData)
            })
            .withProjection(new ManagedModelProjection<I>(viewSchema, delegateSchema, ManagedProxyFactory.INSTANCE, null))
            .withProjection(new ManagedModelProjection<I>(componentSpecInternalSchema, delegateSchema, ManagedProxyFactory.INSTANCE, null))
            .descriptor(descriptor)
        modelRegistry.register(registration.build())

        return modelRegistry.atState(componentId.name, ModelNode.State.Initialized)
    }
}
