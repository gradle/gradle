/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.collections

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.MutationGuard
import org.gradle.api.plugins.ExtensionAware
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultDomainObjectCollectionFactoryTest extends Specification {
    def factory = new DefaultDomainObjectCollectionFactory(TestUtil.instantiatorFactory(), TestUtil.services(), CollectionCallbackActionDecorator.NOOP, Stub(MutationGuard))

    def "creates container with decorated elements"() {
        given:
        def container = factory.newNamedDomainObjectContainer(ElementBean)
        def element = container.create("one")

        expect:
        element instanceof ExtensionAware
    }

    def "creates container with undecorated elements"() {
        given:
        def container = factory.newNamedDomainObjectContainerUndecorated(ElementBean)
        def element = container.create("one")

        expect:
        !(element instanceof ExtensionAware)
    }
}

class ElementBean {
    final String name

    ElementBean(String name) {
        this.name = name
    }
}
