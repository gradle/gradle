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

package org.gradle.language.jvm.internal

import org.gradle.language.base.internal.BinaryNamingScheme
import spock.lang.Specification

class DefaultJvmLibraryBinaryTest extends Specification {
    def namingScheme = Mock(BinaryNamingScheme)

    def "binary takes name and displayName from naming scheme"() {
        when:
        def binary = new DefaultJvmLibraryBinary(namingScheme)

        and:
        namingScheme.lifecycleTaskName >> "jvm-lib-jar"
        namingScheme.description >> "the jar"

        then:
        binary.name == "jvm-lib-jar"
        binary.displayName == "the jar"
    }
}
