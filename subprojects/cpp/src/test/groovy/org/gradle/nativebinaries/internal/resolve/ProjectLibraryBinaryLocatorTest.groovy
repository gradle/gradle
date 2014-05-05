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
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.UnknownProjectException
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.runtime.base.LibraryContainer
import org.gradle.nativebinaries.NativeLibrary
import org.gradle.nativebinaries.NativeLibraryRequirement
import org.gradle.nativebinaries.internal.ProjectNativeLibraryRequirement
import spock.lang.Specification

class ProjectLibraryBinaryLocatorTest extends Specification {
    def project = Mock(ProjectInternal)
    def projectLocator = Mock(ProjectLocator)
    def requirement = Mock(NativeLibraryRequirement)
    def library = Mock(NativeLibrary)
    def binaries = Mock(DomainObjectSet)
    def locator = new ProjectLibraryBinaryLocator(projectLocator)

    def setup() {
        library.binaries >> binaries
    }

    def "locates binaries for library in same project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("libName", null)

        and:
        projectLocator.locateProject(null) >> project
        findLibraryInProject()

        then:
        locator.getBinaries(requirement) == binaries
    }

    def "locates binaries for library in other project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", null)

        and:
        projectLocator.locateProject("other") >> project
        findLibraryInProject()

        then:
        locator.getBinaries(requirement) == binaries
    }

    def "parses map notation for library with static linkage"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", "static")

        and:
        projectLocator.locateProject("other") >> project
        findLibraryInProject()

        then:
        locator.getBinaries(requirement) == binaries
    }

    def "fails for unknown project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("unknown", "libName", "static")

        and:
        projectLocator.locateProject("unknown") >> { throw new UnknownProjectException("unknown")}

        and:
        locator.getBinaries(requirement)

        then:
        thrown(UnknownProjectException)
    }

    def "fails for unknown library"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "unknown", "static")

        and:
        projectLocator.locateProject("other") >> project
        def libraries = findLibraryContainer(project)
        libraries.getByName("unknown") >> { throw new UnknownDomainObjectException("unknown") }

        and:
        locator.getBinaries(requirement)

        then:
        thrown(UnknownDomainObjectException)
    }

    def "fails when project does not have libraries"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", "static")

        and:
        projectLocator.locateProject("other") >> project
        def extensions = Mock(ExtensionContainerInternal)
        project.getExtensions() >> extensions
        extensions.findByName("libraries") >> null
        project.path >> "project-path"

        and:
        locator.getBinaries(requirement)

        then:
        def e = thrown(LibraryResolveException)
        e.message == "Project does not have a libraries container: 'project-path'"
    }

    private void findLibraryInProject() {
        def libraries = findLibraryContainer(project)
        libraries.getByName("libName") >> library
    }

    private findLibraryContainer(ProjectInternal project) {
        def extensions = Mock(ExtensionContainerInternal)
        def libraries = Mock(LibraryContainer)
        def nativeLibraries = Mock(NamedDomainObjectSet)
        project.getExtensions() >> extensions
        extensions.findByName("libraries") >> libraries
        libraries.withType(NativeLibrary) >> nativeLibraries
        return nativeLibraries
    }
}
