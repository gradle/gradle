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

import org.gradle.api.UnknownProjectException
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.model.ModelMap
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.model.internal.type.ModelTypes.modelMap

class ProjectLibraryBinaryLocatorTest extends Specification {
    def projectModel = Mock(ModelRegistry)
    def projectLocator = Mock(ProjectModelResolver)
    def library = Mock(NativeLibrarySpec)
    def binary = Mock(MockNativeLibraryBinary)
    def binaries = Mock(ModelMap)
    def nativeBinaries = Mock(ModelMap)
    def convertedBinaries = TestUtil.domainObjectCollectionFactory().newDomainObjectSet(NativeLibraryBinary)
    def locator = new ProjectLibraryBinaryLocator(projectLocator, TestUtil.domainObjectCollectionFactory())

    def setup() {
        convertedBinaries.add(binary)
        library.binaries >> binaries
        binaries.withType(NativeBinarySpec) >> nativeBinaries
        nativeBinaries.values() >> [binary]
    }

    def "locates binaries for library in other project"() {
        when:
        def requirement = new LibraryIdentifier("other", "libName")

        and:
        projectLocator.resolveProjectModel("other") >> projectModel
        findLibraryInProject()

        then:
        locator.getBinaries(requirement) == convertedBinaries
    }

    def "fails for unknown project"() {
        when:
        def requirement = new LibraryIdentifier("unknown", "libName")
        def failure = new UnknownProjectException("unknown")

        and:
        projectLocator.resolveProjectModel("unknown") >> { throw failure }

        and:
        locator.getBinaries(requirement)

        then:
        def e = thrown(UnknownProjectException)
        e.is(failure)
    }

    def "returns null for unknown library"() {
        when:
        def requirement = new LibraryIdentifier("other", "unknown")

        and:
        projectLocator.resolveProjectModel("other") >> projectModel
        def libraries = findLibraryContainer(projectModel)
        libraries.get("unknown") >> null

        then:
        locator.getBinaries(requirement) == null
    }

    def "returns null when project does not have libraries"() {
        when:
        def requirement = new LibraryIdentifier("other", "libName")

        and:
        projectLocator.resolveProjectModel("other") >> projectModel
        projectModel.find("components", modelMap(NativeLibrarySpec)) >> null

        then:
        locator.getBinaries(requirement) == null
    }

    private void findLibraryInProject() {
        def libraries = findLibraryContainer(projectModel)
        libraries.containsKey("libName") >> true
        libraries.get("libName") >> library
    }

    private findLibraryContainer(ModelRegistry modelRegistry) {
        def components = Mock(ComponentSpecContainer)
        modelRegistry.find("components", ComponentSpecContainer) >> components
        components.withType(NativeLibrarySpec.class) >> components
        return components
    }

    interface MockNativeLibraryBinary extends NativeBinarySpec, NativeLibraryBinary {}

}
