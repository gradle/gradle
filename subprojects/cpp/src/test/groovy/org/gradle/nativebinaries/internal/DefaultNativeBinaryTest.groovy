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

package org.gradle.nativebinaries.internal

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultBinaryNamingScheme
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.nativebinaries.*
import spock.lang.Specification

class DefaultNativeBinaryTest extends Specification {
    def flavor1 = new DefaultFlavor("flavor1")
    def component = new DefaultNativeComponent("name", new DirectInstantiator())
    def toolChain1 = Stub(ToolChainInternal) {
        getName() >> "ToolChain1"
    }
    def platform1 = Stub(Platform) {
        getArchitecture() >> Platform.Architecture.AMD64
    }
    def buildType1 = Stub(BuildType) {
        getName() >> "BuildType1"
    }

    def "binary uses source from its owner component"() {
        given:
        def binary = testBinary(component)
        def sourceSet = Stub(LanguageSourceSet)

        when:
        component.source(sourceSet)

        then:
        binary.source.contains(sourceSet)
    }

    def "binary uses all source sets from a functional source set"() {
        given:
        def binary = testBinary(component)
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
        def binary = testBinary(component, flavor1)
        def library = Mock(Library)
        def resolver = Mock(ContextualLibraryResolver)
        def dependency = Stub(NativeDependencySet)

        given:
        library.shared >> resolver
        resolver.withFlavor(flavor1) >> resolver
        resolver.withToolChain(toolChain1) >> resolver
        resolver.withPlatform(platform1) >> resolver
        resolver.withBuildType(buildType1) >> resolver
        resolver.resolve() >> dependency

        when:
        binary.lib(library)

        then:
        binary.libs.size() == 1
        binary.libs.contains(dependency)
    }

    def "can add a library binary as a dependency of the binary"() {
        def binary = testBinary(component)
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
        def binary = testBinary(component)
        def dependency = Stub(NativeDependencySet)

        when:
        binary.lib(dependency)

        then:
        binary.libs.size() == 1
        binary.libs.contains(dependency)
    }

    def testBinary(NativeComponent owner, Flavor flavor = Flavor.DEFAULT) {
        return new TestBinary(owner, flavor, toolChain1, platform1, buildType1, new DefaultBinaryNamingScheme("baseName"))
    }

    class TestBinary extends DefaultNativeBinary {
        def owner
        TestBinary(NativeComponent owner, Flavor flavor, ToolChainInternal toolChain, Platform targetPlatform, BuildType buildType, DefaultBinaryNamingScheme namingScheme) {
            super(owner, flavor, toolChain, targetPlatform, buildType, namingScheme)
            this.owner = owner
        }

        @Override
        protected NativeComponent getComponent() {
            return owner
        }

        @Override
        String getOutputFileName() {
            return null
        }
    }
}
