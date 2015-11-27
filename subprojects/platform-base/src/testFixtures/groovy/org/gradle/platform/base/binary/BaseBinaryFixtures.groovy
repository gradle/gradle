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

package org.gradle.platform.base.binary

import org.gradle.api.Action
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.TestNodeInitializerRegistry
import org.gradle.model.internal.manage.instance.ManagedProxyFactory
import org.gradle.model.internal.manage.projection.ManagedModelProjection
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.internal.BinarySpecInternal

class BaseBinaryFixtures {
    static final def GENERATOR = new AsmBackedClassGenerator()

    static <T extends BinarySpec, I extends BaseBinarySpec> T create(Class<T> publicType, Class<I> implType, String name, MutableModelNode componentNode, ITaskFactory taskFactory) {
        def modelRegistry = new ModelRegistryHelper()
        def descriptor = new SimpleModelRuleDescriptor("<create $name>")
        modelRegistry.registerInstance("TestNodeInitializerRegistry", TestNodeInitializerRegistry.INSTANCE)

        def viewSchema = DefaultModelSchemaStore.instance.getSchema(publicType)
        def delegateSchema = DefaultModelSchemaStore.instance.getSchema(implType)
        def binarySpecInternalSchema = DefaultModelSchemaStore.instance.getSchema(BinarySpecInternal)

        def registration = ModelRegistrations.of(ModelPath.path(name), (Action) { MutableModelNode node ->
                def generated = GENERATOR.generate(implType)
                def privateData = BaseBinarySpec.create(publicType, generated, name, node, componentNode, DirectInstantiator.INSTANCE, taskFactory)
                node.setPrivateData(implType, privateData)
            })
            .withProjection(new ManagedModelProjection<I>(viewSchema, delegateSchema, ManagedProxyFactory.INSTANCE, null))
            .withProjection(new ManagedModelProjection<I>(binarySpecInternalSchema, delegateSchema, ManagedProxyFactory.INSTANCE, null))
            .descriptor(descriptor)

        modelRegistry.register(registration.build())
        def node = modelRegistry.atState(name, ModelNode.State.Initialized)
        return node.asMutable(ModelType.of(publicType), descriptor).instance
    }
}
