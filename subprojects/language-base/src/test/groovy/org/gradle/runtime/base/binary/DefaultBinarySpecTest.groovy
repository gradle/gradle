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

package org.gradle.runtime.base.binary

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.runtime.base.internal.BinaryNamingScheme
import spock.lang.Specification

class DefaultBinarySpecTest extends Specification {
    def instantiator = new DirectInstantiator()
    def binaryNamingScheme = Mock(BinaryNamingScheme)

    def "binary has name and description"() {
        def binary = DefaultBinarySpec.create(MySampleBinary.class, binaryNamingScheme, instantiator)

        when:
        _ * binaryNamingScheme.lifecycleTaskName >> "sampleBinary"

        then:
        binary.name == "sampleBinary"
        binary.displayName == "MySampleBinary: 'sampleBinary'"
    }


    static class MySampleBinary extends DefaultBinarySpec {
    }
}
