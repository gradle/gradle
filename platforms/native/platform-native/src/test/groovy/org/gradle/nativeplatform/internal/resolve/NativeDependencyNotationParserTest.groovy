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
package org.gradle.nativeplatform.internal.resolve
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.NativeLibraryRequirement
import spock.lang.Specification

class NativeDependencyNotationParserTest extends Specification {
    def parser = NativeDependencyNotationParser.parser()
    def requirement = Mock(NativeLibraryRequirement)
    def library = Mock(NativeLibrarySpec)
    def project = Mock(ProjectInternal)

    def "uses shared variant of library"() {
        when:
        def input = library

        and:
        library.shared >> requirement

        then:
        parser.parseNotation(input) == requirement
    }

    def "parses map notation for library in same project"() {
        when:
        def input = [library: 'libName']
        def dependency = parser.parseNotation(input)

        then:
        dependency.projectPath == null
        dependency.libraryName == "libName"
        dependency.linkage == null
    }

    def "parses map notation for library in other project"() {
        when:
        def input = [project: 'other', library: 'libName']
        def dependency = parser.parseNotation(input)


        then:
        dependency.projectPath == "other"
        dependency.libraryName == "libName"
        dependency.linkage == null
    }

    def "parses map notation for library with defined linkage"() {
        when:
        def input = [project: 'other', library: 'libName', linkage: 'static']
        def dependency = parser.parseNotation(input)

        then:
        dependency.projectPath == "other"
        dependency.libraryName == "libName"
        dependency.linkage == "static"
    }
}
