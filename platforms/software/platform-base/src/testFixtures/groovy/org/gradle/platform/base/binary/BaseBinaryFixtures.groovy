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


import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.NamedEntityInstantiator
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.ComponentSpecInternal
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.test.fixtures.BaseInstanceFixtureSupport
import org.gradle.util.TestUtil

class BaseBinaryFixtures {
    static <T extends BinarySpec, I extends BaseBinarySpec> T create(Class<T> publicType, Class<I> implType, String name, MutableModelNode componentNode) {
        return BaseInstanceFixtureSupport.create(publicType, BinarySpecInternal, implType, name) { MutableModelNode node ->
            def identifier = componentNode ? componentNode.asImmutable(ModelType.of(ComponentSpecInternal), null).instance.identifier.child(name) : new DefaultComponentSpecIdentifier("project", name)
            return BaseBinarySpec.create(publicType, implType, identifier, node, componentNode, TestUtil.instantiatorFactory().injectLenient(), {} as NamedEntityInstantiator, CollectionCallbackActionDecorator.NOOP, TestUtil.domainObjectCollectionFactory())
        }
    }
}
