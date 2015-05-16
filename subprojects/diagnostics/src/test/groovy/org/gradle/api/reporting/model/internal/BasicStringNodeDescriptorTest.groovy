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

package org.gradle.api.reporting.model.internal

import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.MutableModelNode
import spock.lang.Specification

class BasicStringNodeDescriptorTest extends Specification {

    def "should return the path of a node as it's label"() {
        ModelNode modelNode = Mock(ModelNode) {
            getPath() >> Mock(ModelPath) {
                getName() >> 'a path name'
            }
        }
        def descriptor = new BasicStringNodeDescriptor()
        expect:
        descriptor.label(modelNode) == 'a path name'
    }

    def "should not have a value when not a leaf node"() {
        ModelNode modelNode = Mock(MutableModelNode) {
            getLinkCount() >> 1
        }
        def descriptor = new BasicStringNodeDescriptor()

        expect:
        descriptor.value(modelNode).isPresent() == false
    }

    def "should use a leaf nodes private data as it's value"() {
        ModelNode modelNode = Mock(MutableModelNode) {
            getLinkCount() >> 0
            getPrivateData() >> 'a value'
        }
        def descriptor = new BasicStringNodeDescriptor()

        expect:
        descriptor.value(modelNode).get() == 'a value'
    }
}
