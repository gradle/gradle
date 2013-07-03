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

package org.gradle.nativecode.language.cpp.internal

import org.gradle.nativecode.base.Library
import org.gradle.nativecode.base.LibraryBinary
import org.gradle.nativecode.base.NativeDependencySet
import org.gradle.util.HelperUtil
import spock.lang.Specification

class DefaultCppSourceSetTest extends Specification {
    def sourceSet = new DefaultCppSourceSet("cpp", "main", HelperUtil.createRootProject())

    def "has useful string representation"() {
        expect:
        sourceSet.toString() == "C++ source 'mainCpp'"
    }

    def "can add a library as a dependency of the source set"() {
        def dependency = Stub(NativeDependencySet)
        def library = Mock(Library)

        given:
        library.shared >> dependency

        when:
        sourceSet.lib(library)

        then:
        sourceSet.libs.contains(dependency)
    }

    def "can add a library binary as a dependency of the binary"() {
        def dependency = Stub(NativeDependencySet)
        def library = Mock(LibraryBinary)

        given:
        library.asNativeDependencySet >> dependency

        when:
        sourceSet.lib(library)

        then:
        sourceSet.libs.contains(dependency)
    }

    def "can add a native dependency as a dependency of the binary"() {
        def dependency = Stub(NativeDependencySet)

        when:
        sourceSet.lib(dependency)

        then:
        sourceSet.libs.contains(dependency)
    }
}
