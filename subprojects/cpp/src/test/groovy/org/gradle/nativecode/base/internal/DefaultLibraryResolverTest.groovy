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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativecode.base.*
import spock.lang.Specification

class DefaultLibraryResolverTest extends Specification {
    final flavor1 = new DefaultFlavor("flavor1")
    final flavor2 = new DefaultFlavor("flavor2")
    final flavorContainer = new DefaultFlavorContainer(new DirectInstantiator())

    def library = Mock(Library)
    def sharedDeps = Mock(NativeDependencySet)
    def staticDeps = Mock(NativeDependencySet)

    def staticBinary1 = Stub(StaticLibraryBinary) {
        getFlavor() >> flavor1
    }
    def staticBinary2 = Stub(StaticLibraryBinary) {
        getFlavor() >> flavor2
    }
    def sharedBinary1 = Stub(SharedLibraryBinary) {
        getFlavor() >> flavor1
    }
    def sharedBinary2 = Stub(SharedLibraryBinary) {
        getFlavor() >> flavor2
    }

    def "setup"() {
        library.flavors >> flavorContainer
    }

    def "returns library dependencies for library with single default flavor"() {
        when:
        library.getBinaries() >> binaries(staticBinary1, sharedBinary1)

        and:
        sharedBinary1.resolve() >> sharedDeps
        staticBinary1.resolve() >> staticDeps

        then:
        resolver.resolve() == sharedDeps;
        resolver.withType(SharedLibraryBinary.class).resolve() == sharedDeps
        resolver.withType(StaticLibraryBinary.class).resolve() == staticDeps
    }

    def "returns library dependencies for library with single explicit flavor"() {
        when:
        flavorContainer.addAll([flavor2])
        library.getBinaries() >> binaries(staticBinary2, sharedBinary2)

        and:
        sharedBinary2.resolve() >> sharedDeps
        staticBinary2.resolve() >> staticDeps

        then:
        resolver.withFlavor(flavor1).resolve() == sharedDeps;
        resolver.withFlavor(flavor1).withType(SharedLibraryBinary.class).resolve() == sharedDeps
        resolver.withFlavor(flavor1).withType(StaticLibraryBinary.class).resolve() == staticDeps

        and:
        resolver.withFlavor(flavor2).resolve() == sharedDeps;
        resolver.withFlavor(flavor2).withType(SharedLibraryBinary.class).resolve() == sharedDeps
        resolver.withFlavor(flavor2).withType(StaticLibraryBinary.class).resolve() == staticDeps
    }

    def "returns matching library dependencies for library with multiple flavors"() {
        when:
        flavorContainer.addAll([flavor1, flavor2])
        library.getBinaries() >> binaries(staticBinary1, staticBinary2, sharedBinary1, sharedBinary2)

        and:
        sharedBinary2.resolve() >> sharedDeps
        staticBinary2.resolve() >> staticDeps

        then:
        resolver.withFlavor(flavor2).resolve() == sharedDeps;
        resolver.withFlavor(flavor2).withType(SharedLibraryBinary.class).resolve() == sharedDeps
        resolver.withFlavor(flavor2).withType(StaticLibraryBinary.class).resolve() == staticDeps

        when:
        sharedBinary1.resolve() >> sharedDeps
        staticBinary1.resolve() >> staticDeps

        then:
        resolver.withFlavor(flavor1).resolve() == sharedDeps;
        resolver.withFlavor(flavor1).withType(SharedLibraryBinary.class).resolve() == sharedDeps
        resolver.withFlavor(flavor1).withType(StaticLibraryBinary.class).resolve() == staticDeps
    }

    def "fails when no library found with defined flavor"() {
        when:
        flavorContainer.addAll([flavor1, flavor2])
        library.getBinaries() >> binaries(sharedBinary1, sharedBinary2)

        and:
        resolver.withFlavor(new DefaultFlavor("different")).resolve();

        then:
        def e = thrown InvalidUserDataException
        e.message == "No shared library binary available for $library with flavor 'different'"
    }

    def getResolver() {
        return new DefaultLibraryResolver(library)
    }

    def binaries(NativeBinary... values) {
        return new DefaultDomainObjectSet<NativeBinary>(NativeBinary.class, values as List)
    }
}
