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

package org.gradle.runtime.jvm.internal.plugins
import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.runtime.base.Binary
import org.gradle.runtime.base.BinaryContainer
import org.gradle.runtime.base.Library
import org.gradle.runtime.base.internal.BinaryNamingScheme
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder
import org.gradle.runtime.base.internal.DefaultSoftwareComponentContainer
import org.gradle.runtime.jvm.internal.DefaultJvmLibrary
import org.gradle.runtime.jvm.internal.JvmLibraryBinaryInternal
import spock.lang.Specification

class CreateJvmBinariesTest extends Specification {
    def namingSchemeBuilder = Mock(BinaryNamingSchemeBuilder)
    def rule = new CreateJvmBinaries(namingSchemeBuilder)
    def binaries = Mock(BinaryContainer)
    def libraries = new DefaultSoftwareComponentContainer(new DirectInstantiator())

    def "adds a binary for each jvm library"() {
        def library = new DefaultJvmLibrary("jvmLibOne")
        def namingScheme = Mock(BinaryNamingScheme)

        when:
        libraries.add(library)

        and:
        rule.createBinaries(binaries, libraries)

        then:
        1 * namingSchemeBuilder.withComponentName("jvmLibOne") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withTypeString("jar") >> namingSchemeBuilder
        1 * namingSchemeBuilder.build() >> namingScheme
        1 * binaries.add({ JvmLibraryBinaryInternal binary ->
            binary.namingScheme == namingScheme
            binary.library == library
        } as Binary)
        0 * _
    }

    def "does nothing for non-jvm library"() {
        when:
        libraries.add(Stub(OtherLibrary) {
            getName() >> "other-lib"
        })

        and:
        rule.createBinaries(binaries, libraries)

        then:
        0 * _
    }

    interface OtherLibrary extends Library, Named {}
}
