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

import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.TestManagedProxyFactory
import org.gradle.model.internal.fixture.TestNodeInitializerRegistry
import org.gradle.model.internal.manage.projection.ManagedModelProjection
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType

class BaseLanguageSourceSetFixtures {
    static <T extends LanguageSourceSet> T create(Class<T> type, Class<? extends BaseLanguageSourceSet> implType, String name) {
        def modelRegistry = new ModelRegistryHelper()
        def descriptor = new SimpleModelRuleDescriptor("<create $name>")
        modelRegistry.registerInstance("TestNodeInitializerRegistry", TestNodeInitializerRegistry.INSTANCE)
        def reference = ModelReference.of(name, implType)
        modelRegistry.register(
            ModelRegistrations.unmanagedInstanceOf(reference, {
                BaseLanguageSourceSet.create(type, implType, name, null, null)
            })
                .descriptor(descriptor)
                .build()
        )
        def node = modelRegistry.atState(name, ModelNode.State.Initialized)
        def viewSchema = DefaultModelSchemaStore.instance.getSchema(type)
        def delegateSchema = DefaultModelSchemaStore.instance.getSchema(implType)
        return new ManagedModelProjection<T>(viewSchema, delegateSchema, TestManagedProxyFactory.INSTANCE, null).asMutable(ModelType.of(type), node, descriptor).instance
    }

}
