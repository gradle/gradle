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

package org.gradle.language.cpp.internal
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import spock.lang.Specification

class DefaultCppSourceSetTest extends Specification {
    def parent = "main"
    def fileResolver = Mock(FileResolver)
    def sourceSet = BaseLanguageSourceSet.create(DefaultCppSourceSet, "cpp", parent, fileResolver, DirectInstantiator.INSTANCE)

    def "has useful string representation"() {
        expect:
        sourceSet.displayName == "C++ source 'main:cpp'"
        sourceSet.toString() == "C++ source 'main:cpp'"
    }

    def "can add a library as a dependency of the source set"() {
        def library = Mock(NativeLibrarySpec)

        when:
        sourceSet.lib(library)

        then:
        sourceSet.libs.contains(library)
    }

    def "can add a library binary as a dependency of the binary"() {
        def library = Mock(NativeLibraryBinary)

        when:
        sourceSet.lib(library)

        then:
        sourceSet.libs.contains(library)
    }

    def "can add a native dependency as a dependency of the binary"() {
        def dependency = Stub(NativeDependencySet)

        when:
        sourceSet.lib(dependency)

        then:
        sourceSet.libs.contains(dependency)
    }
}
