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

package org.gradle.language.jvm.internal.plugins

import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.BinaryContainer
import org.gradle.language.base.Library
import org.gradle.language.base.internal.DefaultLibraryContainer
import org.gradle.language.jvm.JvmLibraryBinary
import org.gradle.language.jvm.internal.DefaultJvmLibrary
import spock.lang.Specification

class CreateJvmBinariesTest extends Specification {
    def rule = new CreateJvmBinaries()
    def binaries = Mock(BinaryContainer)
    def libraries = new DefaultLibraryContainer(new DirectInstantiator())

    def "adds a binary for each jvm library"() {
        when:
        libraries.add(new DefaultJvmLibrary("jvmLibOne"))

        and:
        rule.createBinaries(binaries, libraries)

        then:
        1 * binaries.create("jvmLibOneBinary", JvmLibraryBinary)
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
