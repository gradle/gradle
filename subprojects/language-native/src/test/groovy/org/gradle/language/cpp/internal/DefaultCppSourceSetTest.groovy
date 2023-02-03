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


import org.gradle.language.base.sources.BaseLanguageSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultCppSourceSetTest extends Specification {
    def sourceSet = BaseLanguageSourceSet.create(CppSourceSet, DefaultCppSourceSet, new DefaultComponentSpecIdentifier("project", "cpp"), TestUtil.objectFactory())

    def "has useful string representation"() {
        expect:
        sourceSet.displayName == "C++ source 'cpp'"
        sourceSet.toString() == "C++ source 'cpp'"
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
