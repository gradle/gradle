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

package org.gradle.language.base.sources

import org.gradle.api.Action
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.LanguageSourceSetInternal
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

class BaseLanguageSourceSetFixtures {
    static <T extends LanguageSourceSet> T create(Class<T> publicType, Class<? extends BaseLanguageSourceSet> implType, String name) {
        def modelRegistry = new ModelRegistryHelper()
        modelRegistry.registerInstance("TestNodeInitializerRegistry", TestNodeInitializerRegistry.INSTANCE)

        def viewSchema = DefaultModelSchemaStore.instance.getSchema(publicType)
        def languageSourceSetInternalSchema = DefaultModelSchemaStore.instance.getSchema(LanguageSourceSetInternal)
        def delegateSchema = DefaultModelSchemaStore.instance.getSchema(implType)

        def descriptor = new SimpleModelRuleDescriptor("<create $name>")

        def registration = ModelRegistrations.of(ModelPath.path(name), (Action) { MutableModelNode node ->
                def privateData = BaseLanguageSourceSet.create(publicType, implType, name, null, null)
                node.setPrivateData(implType, privateData)
            })
            .withProjection(new ManagedModelProjection<T>(viewSchema, delegateSchema, ManagedProxyFactory.INSTANCE, null))
            .withProjection(new ManagedModelProjection<T>(languageSourceSetInternalSchema, delegateSchema, ManagedProxyFactory.INSTANCE, null))
            .descriptor(descriptor)

        modelRegistry.register(registration.build())

        def node = modelRegistry.atState(name, ModelNode.State.Initialized)
        return node.asMutable(ModelType.of(publicType), descriptor).instance
    }
}
