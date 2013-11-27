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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativebinaries.*
import spock.lang.Ignore
import spock.lang.Specification

class DefaultLibraryResolverTest extends Specification {
    final flavor1 = new DefaultFlavor("flavor1")
    final flavor2 = new DefaultFlavor("flavor2")
    final flavorContainer = new DefaultFlavorContainer(new DirectInstantiator())
    final toolChain1 = Stub(ToolChain) {
        getName() >> "ToolChain1"
    }
    final toolChain2 = Stub(ToolChain) {
        getName() >> "ToolChain2"
    }
    final platform1 = Stub(Platform) {
        getName() >> "Platform1"
    }
    final platform2 = Stub(Platform) {
        getName() >> "Platform2"
    }

    def library = Mock(Library)
    def resolver = new DefaultLibraryResolver(library)
    def deps1 = Mock(NativeDependencySet)
    def deps2 = Mock(NativeDependencySet)

    def "setup"() {
        library.flavors >> flavorContainer

        resolver.withFlavor(flavor1).withToolChain(toolChain1).withPlatform(platform1).withType(SharedLibraryBinary)
    }

    def "resolves dependencies for library binary with matching type"() {
        when:
        library.getBinaries() >>
                binaries(
                        staticBinary(flavor1, toolChain1, platform1, deps1),
                        sharedBinary(flavor1, toolChain1, platform1, deps2)
                )

        then:
        resolver.withType(StaticLibraryBinary).resolve() == deps1
        resolver.withType(SharedLibraryBinary).resolve() == deps2
    }

    def "resolves dependencies for library binary with matching flavor"() {
        when:
        flavorContainer.addAll([flavor1, flavor2])
        library.getBinaries() >>
                binaries(
                        sharedBinary(flavor1, toolChain1, platform1, deps1),
                        sharedBinary(flavor2, toolChain1, platform1, deps2)
                )

        then:
        resolver.withFlavor(flavor1).resolve() == deps1
        resolver.withFlavor(flavor2).resolve() == deps2
    }

    @Ignore("TODO:DAZ fix this with real dependency resolution")
    def "resolves dependencies for library binary when library has a single flavor"() {
        when:
        flavorContainer.addAll([flavor2])
        library.getBinaries() >>
                binaries(
                        sharedBinary(flavor2, toolChain1, platform1, deps1)
                )

        then:
        resolver.withFlavor(flavor1).resolve() == deps1
        resolver.withFlavor(flavor2).resolve() == deps1
    }

    def "resolves dependencies for library binary with matching tool chain"() {
        when:
        library.getBinaries() >>
                binaries(
                        sharedBinary(flavor1, toolChain1, platform1, deps1),
                        sharedBinary(flavor1, toolChain2, platform1, deps2)
                )

        then:
        resolver.withToolChain(toolChain1).resolve() == deps1
        resolver.withToolChain(toolChain2).resolve() == deps2
    }

    def "resolves dependencies for library binary with matching platform"() {
        when:
        library.getBinaries() >>
                binaries(
                        sharedBinary(flavor1, toolChain1, platform1, deps1),
                        sharedBinary(flavor1, toolChain1, platform2, deps2)
                )

        then:
        resolver.withPlatform(platform1).resolve() == deps1
        resolver.withPlatform(platform2).resolve() == deps2
    }

    def "fails when no library found with matching flavor"() {
        when:
        library.getBinaries() >>
                binaries(
                        sharedBinary(flavor1, toolChain1, platform1, deps1)
                )

        and:
        resolver.withToolChain(toolChain2).resolve();

        then:
        def e = thrown InvalidUserDataException
        e.message == "No shared library binary available for $library with [flavor: 'flavor1', toolChain: 'ToolChain2', platform: 'Platform1']"
    }

    def staticBinary(def flavor, def toolChain, def platform, def deps) {
        return Stub(StaticLibraryBinary) {
            getFlavor() >> flavor
            getToolChain() >> toolChain
            getTargetPlatform() >> platform
            resolve() >> deps
        }
    }

    def sharedBinary(def flavor, def toolChain, def platform, def deps) {
        return Stub(SharedLibraryBinary) {
            getFlavor() >> flavor
            getToolChain() >> toolChain
            getTargetPlatform() >> platform
            resolve() >> deps
        }
    }

    def binaries(NativeBinary... values) {
        return new DefaultDomainObjectSet<NativeBinary>(NativeBinary.class, values as List)
    }
}
