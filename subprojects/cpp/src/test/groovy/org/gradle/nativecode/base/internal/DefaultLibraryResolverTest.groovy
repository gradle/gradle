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

import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.nativecode.base.Flavor
import org.gradle.nativecode.base.Library
import org.gradle.nativecode.base.NativeBinary
import org.gradle.nativecode.base.NativeDependencySet
import org.gradle.nativecode.base.SharedLibraryBinary
import org.gradle.nativecode.base.StaticLibraryBinary
import spock.lang.Specification

class DefaultLibraryResolverTest extends Specification {
    def library = Mock(Library)
    def resolver = new DefaultLibraryResolver(library)
    def sharedDeps = Mock(NativeDependencySet)
    def staticDeps = Mock(NativeDependencySet)
    def defaultStaticBinary = Stub(StaticLibraryBinary) {
        getFlavor() >> new DefaultFlavor(Flavor.DEFAULT)
    }
    def flavoredStaticBinary = Stub(StaticLibraryBinary) {
        getFlavor() >> new DefaultFlavor("another")
    }
    def defaultSharedBinary = Stub(SharedLibraryBinary) {
        getFlavor() >> new DefaultFlavor(Flavor.DEFAULT)
    }
    def flavoredSharedBinary = Stub(SharedLibraryBinary) {
        getFlavor() >> new DefaultFlavor("another")
    }

    final allBinaries = new DefaultDomainObjectSet<NativeBinary>(NativeBinary.class, [defaultStaticBinary, flavoredStaticBinary, flavoredSharedBinary, defaultSharedBinary])

    def "returns library dependencies for default flavor"() {
        when:
        library.getBinaries() >> allBinaries

        and:
        defaultSharedBinary.resolve() >> sharedDeps
        defaultStaticBinary.resolve() >> staticDeps

        then:
        resolver.resolve() == sharedDeps;
        resolver.withType(SharedLibraryBinary.class).resolve() == sharedDeps
        resolver.withType(StaticLibraryBinary.class).resolve() == staticDeps
    }

    def "returns static library with defined flavor"() {
        final flavor = new DefaultFlavor("another")
        when:
        library.getBinaries() >> allBinaries

        and:
        flavoredSharedBinary.resolve() >> sharedDeps
        flavoredStaticBinary.resolve() >> staticDeps

        then:
        resolver.withFlavor(flavor).resolve() == sharedDeps;
        resolver.withFlavor(flavor).withType(SharedLibraryBinary.class).resolve() == sharedDeps
        resolver.withFlavor(flavor).withType(StaticLibraryBinary.class).resolve() == staticDeps
    }
}
