/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.fixture.ModelRegistryHelper
import spock.lang.Ignore
import spock.lang.Specification

class ModelNodeReplacementTest extends Specification {

    def registry = new ModelRegistryHelper()

    def "can replace known node"() {
        when:
        registry.registerInstance("foo", "foo")
        registry.replace(registry.registration("foo").unmanaged("bar"))

        then:
        registry.get("foo") == "bar"
    }

    def "cannot replace realized node"() {
        when:
        registry.registerInstance("foo", "foo")

        then:
        registry.get("foo") == "foo"

        when:
        registry.replace(registry.registration("foo").unmanaged("bar"))

        then:
        thrown IllegalStateException
    }

    @Ignore("The whole reuse architecture needs a rethink")
    def "cannot replace node with different type"() {
        when:
        registry.registerInstance("foo", "foo")
        registry.atState("foo", ModelNode.State.Discovered)
        registry.replace(registry.registration("foo").unmanaged(2))

        then:
        thrown IllegalStateException
    }
}
