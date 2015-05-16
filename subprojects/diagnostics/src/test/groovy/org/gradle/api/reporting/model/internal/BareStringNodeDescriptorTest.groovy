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
import spock.lang.Specification

class BareStringNodeDescriptorTest extends Specification {

    def "should have a label but no value"() {
        BareStringNodeDescriptor bareStringNodeDescriptor = new BareStringNodeDescriptor()
        ModelNode modelNode = Mock(ModelNode) {
            getPath() >> Mock(ModelPath) {
                getName() >> 'a path name'
            }
        }
        expect:
        bareStringNodeDescriptor.label(modelNode) == 'a path name'
        bareStringNodeDescriptor.value(modelNode).isPresent() == false

    }
}
