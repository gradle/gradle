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

import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.TestNodeInitializerRegistry

class BaseLanguageSourceSetFixtures {
    static final def GENERATOR = new AsmBackedClassGenerator()

    static <T extends BaseLanguageSourceSet> T create(Class<T> type, Class<T> implType, String name) {
        def modelRegistry = new ModelRegistryHelper()
        modelRegistry.registerInstance("TestNodeInitializerRegistry", TestNodeInitializerRegistry.INSTANCE)
        modelRegistry.register(
            ModelRegistrations.unmanagedInstanceOf(ModelReference.of(name, type), {
                def decorated = GENERATOR.generate(implType)
                BaseLanguageSourceSet.create(type, decorated, name, null, null)
            })
                .descriptor(name)
                .build()
        ).atState(name, ModelNode.State.Initialized).getPrivateData(type)
    }

}
