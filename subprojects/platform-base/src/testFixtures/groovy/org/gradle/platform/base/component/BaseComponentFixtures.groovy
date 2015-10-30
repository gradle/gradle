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

import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.internal.registry.DefaultLanguageRegistry
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.platform.base.ComponentSpecIdentifier

class BaseComponentFixtures {

    static <T extends BaseComponentSpec> T create(Class<T> type, ModelRegistryHelper modelRegistry, ComponentSpecIdentifier componentId, Instantiator instantiator, File baseDir = null) {
        try {
            modelRegistry.register(
                ModelRegistrations.unmanagedInstanceOf(ModelReference.of(componentId.name, type), {
                    BaseComponentSpec.create(type, componentId, it, instantiator, new DefaultLanguageRegistry(), baseDir)
                })
                    .descriptor(componentId.name)
                    .build()
            ).atState(componentId.name, ModelNode.State.Initialized).getPrivateData(type)
        } catch (ModelRuleExecutionException e) {
            throw e.cause
        }
    }

}
