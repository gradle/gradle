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

import org.gradle.api.DomainObjectSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.UnknownProjectException
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.ProjectNativeLibraryRequirement
import spock.lang.Specification

class LibraryBinaryLocatorTest extends Specification {
    def project = Mock(ProjectInternal)
    def projectFinder = Mock(ProjectFinder)
    def requirement = Mock(NativeLibraryRequirement)
    def library = Mock(Library)
    def binaries = Mock(DomainObjectSet)
    def candidates = new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [Mock(NativeBinary), Mock(NativeBinary)])
    def locator = new LibraryBinaryLocator(projectFinder)

    def setup() {
        library.binaries >> binaries
    }

    def "locates binaries for library in same project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("libName", null)

        and:
        projectFinder.getProject(null) >> project
        findLibraryInProject()
        binaries.withType(SharedLibraryBinary) >> candidates

        then:
        locator.getCandidateBinaries(requirement) == candidates
    }

    def "locates binaries for library in other project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", null)

        and:
        projectFinder.getProject("other") >> project
        findLibraryInProject()
        binaries.withType(SharedLibraryBinary) >> candidates

        then:
        locator.getCandidateBinaries(requirement) == candidates
    }

    def "parses map notation for library with static linkage"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", "static")

        and:
        projectFinder.getProject("other") >> project
        findLibraryInProject()
        binaries.withType(StaticLibraryBinary) >> candidates

        then:
        locator.getCandidateBinaries(requirement) == candidates
    }

    def "fails for unknown project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("unknown", "libName", "static")

        and:
        projectFinder.getProject("unknown") >> { throw new UnknownProjectException("unknown")}

        and:
        locator.getCandidateBinaries(requirement)

        then:
        thrown(UnknownProjectException)
    }

    def "fails for unknown library"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "unknown", "static")

        and:
        projectFinder.getProject("other") >> project
        def libraries = findLibraryContainer(project)
        libraries.getByName("unknown") >> { throw new UnknownDomainObjectException("unknown") }

        and:
        locator.getCandidateBinaries(requirement)

        then:
        thrown(UnknownDomainObjectException)
    }

    def "fails when project does not have libraries"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", "static")

        and:
        projectFinder.getProject("other") >> project
        def extensions = Mock(ExtensionContainerInternal)
        project.getExtensions() >> extensions
        extensions.findByName("libraries") >> null
        project.path >> "project-path"

        and:
        locator.getCandidateBinaries(requirement)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Project does not have a libraries container: 'project-path'"
    }

    private void findLibraryInProject() {
        def libraries = findLibraryContainer(project)
        libraries.getByName("libName") >> library
    }
    private LibraryContainer findLibraryContainer(ProjectInternal project) {
        def extensions = Mock(ExtensionContainerInternal)
        def libraries = Mock(LibraryContainer)
        project.getExtensions() >> extensions
        extensions.findByName("libraries") >> libraries
        return libraries
    }
}
