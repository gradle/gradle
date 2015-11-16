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
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.TestNodeInitializerRegistry
import org.gradle.platform.base.internal.ComponentSpecInternal

class BaseBinaryFixtures {

    private final modelRegistry

    BaseBinaryFixtures() {
        modelRegistry = new ModelRegistryHelper()
        modelRegistry.registerInstance("TestNodeInitializerRegistry", TestNodeInitializerRegistry.INSTANCE)
    }

    static <T extends BaseBinarySpec> T create(Class<T> type, String name, ComponentSpecInternal componentSpecInternal, ITaskFactory taskFactory) {
        new BaseBinaryFixtures().createBinary(type, name, componentSpecInternal, taskFactory);
    }

    public <T extends BaseBinarySpec> T createBinary(Class<T> type, String name, ComponentSpecInternal componentSpecInternal, ITaskFactory taskFactory) {
        try {
            modelRegistry.register(
                ModelRegistrations.unmanagedInstanceOf(ModelReference.of(name, type), {
                    BaseBinarySpec.create(type, type, name, it, componentSpecInternal, DirectInstantiator.INSTANCE, taskFactory)
                })
                    .descriptor(name)
                    .build()
            ).atState(name, ModelNode.State.Initialized).getPrivateData(type)
        } catch (ModelRuleExecutionException e) {
            throw e.cause
        }
    }

}
