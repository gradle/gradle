/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base.internal

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.nativecode.base.Library
import org.gradle.nativecode.base.LibraryBinary
import org.gradle.nativecode.base.NativeComponent
import org.gradle.nativecode.base.NativeDependencySet
import spock.lang.Specification

class DefaultNativeBinaryTest extends Specification {
    def component = new DefaultNativeComponent("name")

    def "can generate names for binary"() {
        expect:
        def binary = new TestBinary(component, "binary")
        binary.namingScheme.getTaskName("link") == 'linkBinary'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileBinaryCpp'
    }

    def "binary uses source from its owner component"() {
        given:
        def binary = new TestBinary(component)
        def sourceSet = Stub(LanguageSourceSet)

        when:
        component.source(sourceSet)

        then:
        binary.source.contains(sourceSet)
    }

    def "binary uses all source sets from a functional source set"() {
        given:
        def binary = new TestBinary(component)
        def functionalSourceSet = new DefaultFunctionalSourceSet("func", new DirectInstantiator())
        def sourceSet1 = Stub(LanguageSourceSet) {
            getName() >> "ss1"
        }
        def sourceSet2 = Stub(LanguageSourceSet) {
            getName() >> "ss2"
        }

        when:
        functionalSourceSet.add(sourceSet1)

        and:
        binary.source functionalSourceSet

        and:
        functionalSourceSet.add(sourceSet2)

        then:
        binary.source.contains(sourceSet1)
        binary.source.contains(sourceSet2)
    }

    def "can add a library as a dependency of the binary"() {
        def binary = new TestBinary(component)
        def dependency = Stub(NativeDependencySet)
        def library = Mock(Library)

        given:
        library.shared >> dependency

        when:
        binary.lib(library)

        then:
        binary.libs.size() == 1
        binary.libs.contains(dependency)
    }

    def "can add a library binary as a dependency of the binary"() {
        def binary = new TestBinary(component)
        def dependency = Stub(NativeDependencySet)
        def library = Mock(LibraryBinary)

        given:
        library.asNativeDependencySet >> dependency

        when:
        binary.lib(library)

        then:
        binary.libs.size() == 1
        binary.libs.contains(dependency)
    }

    def "can add a native dependency as a dependency of the binary"() {
        def binary = new TestBinary(component)
        def dependency = Stub(NativeDependencySet)

        when:
        binary.lib(dependency)

        then:
        binary.libs.size() == 1
        binary.libs.contains(dependency)
    }

    class TestBinary extends DefaultNativeBinary {
        TestBinary(NativeComponent owner, String name = "name") {
            super(owner, name, null)
        }

        @Override
        protected NativeComponent getComponent() {
            throw new UnsupportedOperationException()
        }

        @Override
        String getOutputFileName() {
            throw new UnsupportedOperationException()
        }
    }
}
