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
package org.gradle.nativebinaries.internal.resolve

import org.gradle.api.InvalidUserDataException
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.UnknownProjectException
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.nativebinaries.*
import spock.lang.Specification

class NativeDependencyNotationParserTest extends Specification {
    def projectFinder = Mock(ProjectFinder)
    def parser = NativeDependencyNotationParser.parser(projectFinder)
    def nativeDependency = Mock(NativeLibraryDependency)
    def library = Mock(Library)
    def project = Mock(ProjectInternal)

    def "parses library"() {
        when:
        def input = library

        and:
        library.shared >> nativeDependency

        then:
        parser.parseNotation(input) == nativeDependency
    }

    def "parses map notation for library in same project"() {
        when:
        def input = [library: 'libName']

        and:
        projectFinder.getProject(null) >> project
        def dependency = parser.parseNotation(input)

        and:
        def libraries = findLibraryContainer(project)
        libraries.getByName("libName") >> library

        then:
        dependency.library == library
        dependency.type == SharedLibraryBinary
    }

    def "parses map notation for library in other project"() {
        when:
        def input = [project: 'other', library: 'libName']

        and:
        projectFinder.getProject("other") >> project
        def dependency = parser.parseNotation(input)

        and:
        def libraries = findLibraryContainer(project)
        libraries.getByName("libName") >> library

        then:
        dependency.library == library
        dependency.type == SharedLibraryBinary
    }

    def "parses map notation for library with static linkage"() {
        when:
        def input = [project: 'other', library: 'libName', linkage: 'static']

        and:
        projectFinder.getProject("other") >> project
        def dependency = parser.parseNotation(input)

        and:
        def libraries = findLibraryContainer(project)
        libraries.getByName("libName") >> library

        then:
        dependency.library == library
        dependency.type == StaticLibraryBinary
    }

    def "fails for unknown project"() {
        when:
        def input = [project: 'unknown', library: 'libName']

        and:
        projectFinder.getProject("unknown") >> { throw new UnknownProjectException("unknown")}

        and:
        parser.parseNotation(input).library

        then:
        thrown(UnknownProjectException)
    }

    def "fails for unknown library"() {
        when:
        def input = [project: 'other', library: 'libName']

        and:
        projectFinder.getProject("other") >> project
        def libraries = findLibraryContainer(project)
        libraries.getByName("libName") >> { throw new UnknownDomainObjectException("libName") }

        and:
        parser.parseNotation(input).library

        then:
        thrown(UnknownDomainObjectException)
    }

    def "fails when project does not have libraries"() {
        when:
        def input = [project: 'other', library: 'libName']

        and:
        projectFinder.getProject("other") >> project
        def extensions = Mock(ExtensionContainerInternal)
        project.getExtensions() >> extensions
        extensions.findByName("libraries") >> null
        project.getPath() >> "project-path"

        and:
        parser.parseNotation(input).library

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Project does not have a libraries container: 'project-path'"
    }

    private LibraryContainer findLibraryContainer(ProjectInternal project) {
        def extensions = Mock(ExtensionContainerInternal)
        def libraries = Mock(LibraryContainer)
        project.getExtensions() >> extensions
        extensions.findByName("libraries") >> libraries
        return libraries
    }
}
