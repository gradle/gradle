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
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.UnknownProjectException
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.language.base.internal.resolve.LibraryResolveException
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibraryRequirement
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.internal.ProjectNativeLibraryRequirement
import org.gradle.platform.base.ComponentSpecContainer
import spock.lang.Specification

class ProjectLibraryBinaryLocatorTest extends Specification {
    def projectModel = Mock(ModelRegistry)
    def projectLocator = Mock(ProjectModelResolver)
    def requirement = Mock(NativeLibraryRequirement)
    def library = Mock(NativeLibrarySpec)
    def binary = Mock(MockNativeLibraryBinary)
    def binaries = Mock(ModelMap)
    def nativeBinaries = Mock(ModelMap)
    def convertedBinaries = new DefaultDomainObjectSet(NativeLibraryBinary, [binary])
    def locator = new ProjectLibraryBinaryLocator(projectLocator)

    def setup() {
        library.binaries >> binaries
        binaries.withType(NativeBinarySpec) >> nativeBinaries
        nativeBinaries.values() >> [binary]
    }

    def "locates binaries for library in same project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("libName", null)

        and:
        projectLocator.resolveProjectModel(null) >> projectModel
        findLibraryInProject()

        then:
        locator.getBinaries(requirement) == convertedBinaries
    }

    def "locates binaries for library in other project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", null)

        and:
        projectLocator.resolveProjectModel("other") >> projectModel
        findLibraryInProject()

        then:
        locator.getBinaries(requirement) == convertedBinaries
    }

    def "parses map notation for library with static linkage"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", "static")

        and:
        projectLocator.resolveProjectModel("other") >> projectModel
        findLibraryInProject()

        then:
        locator.getBinaries(requirement) == convertedBinaries
    }

    def "fails for unknown project"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("unknown", "libName", "static")

        and:
        projectLocator.resolveProjectModel("unknown") >> { throw new UnknownProjectException("unknown")}

        and:
        locator.getBinaries(requirement)

        then:
        thrown(UnknownProjectException)
    }

    def "fails for unknown library"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "unknown", "static")

        and:
        projectLocator.resolveProjectModel("other") >> projectModel
        def libraries = findLibraryContainer(projectModel)
        libraries.get("unknown") >> { null }

        and:
        locator.getBinaries(requirement)

        then:
        thrown(UnknownDomainObjectException)
    }

    def "fails when project does not have libraries"() {
        when:
        requirement = new ProjectNativeLibraryRequirement("other", "libName", "static")

        and:
        projectLocator.resolveProjectModel("other") >> projectModel
        projectModel.find(ModelPath.path("components"), ModelTypes.modelMap(NativeLibrarySpec)) >> null

        and:
        locator.getBinaries(requirement)

        then:
        def e = thrown(LibraryResolveException)
        e.message == "Project does not have a libraries container: 'other'"
    }

    private void findLibraryInProject() {
        def libraries = findLibraryContainer(projectModel)
        libraries.containsKey("libName") >> true
        libraries.get("libName") >> library
    }

    private findLibraryContainer(ModelRegistry modelRegistry) {
        def components = Mock(ComponentSpecContainer)
        modelRegistry.find(ModelPath.path("components"), ModelType.of(ComponentSpecContainer)) >> components
        components.withType(NativeLibrarySpec.class) >> components
        return components
    }

    interface MockNativeLibraryBinary extends NativeBinarySpec, NativeLibraryBinary {}

}
