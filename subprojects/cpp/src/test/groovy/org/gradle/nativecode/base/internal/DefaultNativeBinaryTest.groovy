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
import org.gradle.nativecode.base.*
import spock.lang.Specification

class DefaultNativeBinaryTest extends Specification {
    def flavor1 = new DefaultFlavor("flavor1")
    def component = new DefaultNativeComponent("name", new DirectInstantiator())

    def "does not use flavor in names name when component has only default flavor"() {
        when:
        def binary = new TestBinary(component, Flavor.DEFAULT, "type")

        then:
        component.flavors == [Flavor.DEFAULT] as Set

        and:
        binary.namingScheme.lifecycleTaskName == 'nameType'
        binary.namingScheme.outputDirectoryBase == 'nameType'
        binary.namingScheme.getTaskName("link") == 'linkNameType'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileNameTypeCpp'
    }

    def "does not use flavor in names when component has only one configured flavor"() {
        when:
        component.flavors.add(flavor1)

        then:
        def binary = new TestBinary(component, flavor1, "type")
        binary.namingScheme.lifecycleTaskName == 'nameType'
        binary.namingScheme.outputDirectoryBase == 'nameType'
        binary.namingScheme.getTaskName("link") == 'linkNameType'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileNameTypeCpp'
    }

    def "includes flavor in names when component has multiple flavors"() {
        when:
        component.flavors.add(Flavor.DEFAULT)
        component.flavors.add(flavor1)

        then:
        def binary = new TestBinary(component, flavor1, "type")
        binary.namingScheme.lifecycleTaskName == 'flavor1NameType'
        binary.namingScheme.outputDirectoryBase == 'nameType/flavor1'
        binary.namingScheme.getTaskName("link") == 'linkFlavor1NameType'
        binary.namingScheme.getTaskName("compile", "cpp") == 'compileFlavor1NameTypeCpp'
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
        functionalSourceSet.add(sourceSet2)

        and:
        binary.source functionalSourceSet

        then:
        binary.source.contains(sourceSet1)
        binary.source.contains(sourceSet2)
    }

    def "can add a resolved library as a dependency of the binary"() {
        def binary = new TestBinary(component, flavor1)
        def library = Mock(Library)
        def resolver = Mock(ConfigurableLibraryResolver)
        def dependency = Stub(NativeDependencySet)

        given:
        library.shared >> resolver
        resolver.withFlavor(flavor1) >> resolver
        resolver.resolve() >> dependency

        when:
        binary.lib(library)

        then:
        binary.libs.size() == 1
        binary.libs.contains(dependency)
    }

    def "can add a library binary as a dependency of the binary"() {
        def binary = new TestBinary(component)
        def dependency = Stub(NativeDependencySet)
        def libraryBinary = Mock(LibraryBinary)

        given:
        libraryBinary.resolve() >> dependency

        when:
        binary.lib(libraryBinary)

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
        TestBinary(NativeComponent owner, Flavor flavor = Flavor.DEFAULT, String type = "type") {
            super(owner, flavor, type, null)
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
