/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.resolve
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.LibraryComponentIdentifier
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.internal.component.model.*
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult
import org.gradle.jvm.JvmLibrarySpec
import org.gradle.model.ModelMap
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.LibrarySpec
import spock.lang.Specification
import spock.lang.Unroll

class LocalLibraryDependencyResolverTest extends Specification {

    Map<String, Project> projects
    ProjectLocator locator
    ProjectFinder finder
    Project rootProject
    LocalLibraryDependencyResolver resolver
    DependencyMetaData metadata
    LibraryComponentSelector selector
    ModuleVersionSelector requested

    def setup() {
        projects = [:]
        finder = Mock(ProjectFinder)
        finder.getProject(_) >> {
            String name = it[0]
            projects[name]
        }
        locator = new DefaultProjectLocator(finder)
        rootProject = mockProject(':')
        resolver = new LocalLibraryDependencyResolver(locator, Mock(BinarySpecToArtifactConverterRegistry))
        metadata = Mock(DependencyMetaData)
        selector = Mock(LibraryComponentSelector)
        requested = Mock(ModuleVersionSelector)
        metadata.requested >> requested
        metadata.selector >> selector

    }

    private ProjectInternal mockProject(String path) {
        def mock = Mock(ProjectInternal)
        mock.findProject(':') >> mock
        mock.path >> ':'
        mock.modelRegistry >> Mock(ModelRegistry)
        mock.tasks >> Mock(TaskContainerInternal)
        projects[path] = mock
        mock
    }

    @Unroll("Resolution for library #lib on project #projectPath completes")
    def "can resolve the library defined in a project"() {
        given:
        requested.group >> projectPath
        requested.name >> lib
        selector.projectPath >> projectPath
        selector.libraryName >> lib
        mockLibraries(rootProject, rootProjectComponents)

        subprojects.each { name, libs ->
            def pj = mockProject(":$name")
            mockLibraries(pj, libs)
        }


        def result = new DefaultBuildableComponentIdResolveResult()

        when:
        resolver.resolve(metadata, result)

        then:
        result.hasResult()
        if (failure) {
            assert result.failure.cause.message =~ failure
        } else {
            assert result.failure == null
            def md = result.metaData
            assert md.id.group == selector.projectPath
            if (selector.libraryName) {
                assert md.id.name == selector.libraryName
            } else {
                assert md.id.name.length() > 0
            }
        }

        where:
        lib    | projectPath | rootProjectComponents | subprojects            | failure
        'lib'  | ':'         | ['lib']               | [:]                    | false
        ''     | ':'         | ['lib']               | [:]                    | false
        'lib'  | ':'         | ['lib', 'lib2']       | [:]                    | false
        'lib2' | ':'         | ['lib', 'lib2']       | [:]                    | false
        'lib2' | ':'         | ['lib']               | [:]                    | "Did you want to use 'lib'"
        'lib2' | ':'         | ['lib', 'lib3']       | [:]                    | "Did you want to use one of 'lib', 'lib3'"
        ''     | ':'         | ['lib', 'lib2']       | [:]                    | "Project ':' contains more than one library. Please select one of 'lib', 'lib2'"
        'lib'  | ':foo'      | ['lib']               | [:]                    | "Project ':foo' not found."
        'lib'  | ':'         | null                  | [:]                    | "Project ':' doesn't define any library"
        'lib'  | ':'         | []                    | [:]                    | "Project ':' doesn't define any library"
        'lib'  | ':foo'      | []                    | [foo: ['lib']]         | false
        'lib'  | ':foo'      | []                    | [foo: ['lib', 'lib2']] | false
        'lib'  | ':foo'      | []                    | [foo: ['lib2']]        | "Did you want to use 'lib2'"
        'lib2' | ':foo'      | []                    | [foo: ['lib', 'lib3']] | "Did you want to use one of 'lib', 'lib3'"
        ''     | ':foo'      | []                    | [foo: ['lib', 'lib2']] | "Project ':foo' contains more than one library. Please select one of 'lib', 'lib2'"
        ''     | ':foo'      | []                    | [foo: null]            | "Project ':foo' doesn't define any library"
        ''     | ':foo'      | []                    | [foo: []]              | "Project ':foo' doesn't define any library"

    }

    def "handles library artifacts"() {
        given:
        def artifact = Mock(ComponentArtifactMetaData)
        def result = new DefaultBuildableArtifactResolveResult()
        artifact.componentId >> Mock(LibraryComponentIdentifier)

        when:
        resolver.resolveArtifact(artifact, Mock(ModuleSource), result)

        then:
        result.hasResult()
    }

    def "ignores non library artifacts"() {
        given:
        def artifact = Mock(ComponentArtifactMetaData)
        def result = new DefaultBuildableArtifactResolveResult()
        artifact.componentId >> Mock(ModuleComponentIdentifier)

        when:
        resolver.resolveArtifact(artifact, Mock(ModuleSource), result)

        then:
        !result.hasResult()
    }

    @Unroll
    def "handles library module artifacts for #type"() {
        given:
        def component = Mock(ComponentResolveMetaData)
        def result = new DefaultBuildableArtifactSetResolveResult()
        component.componentId >> Mock(LibraryComponentIdentifier)

        when:
        resolver.resolveModuleArtifacts(component, type, result)

        then:
        result.hasResult()

        where:
        type << [Mock(ComponentUsage), ArtifactType.SOURCES]
    }

    @Unroll
    def "ignores non library module artifacts for #type"() {
        given:
        def component = Mock(ComponentResolveMetaData)
        def result = new DefaultBuildableArtifactSetResolveResult()
        component.componentId >> Mock(ModuleComponentIdentifier)

        when:
        resolver.resolveModuleArtifacts(component, type, result)

        then:
        !result.hasResult()

        where:
        type << [Mock(ComponentUsage), ArtifactType.SOURCES]
    }

    private ModelMap<? extends LibrarySpec> mockLibraries(Project project, List<String> libraries) {
        if (libraries == null) {
            project.modelRegistry.find(_, _) >> null
        } else {
            ComponentSpecContainer components = Mock()
            project.modelRegistry.find(_, _) >> components
            def map = Mock(ModelMap)
            def librarySpecs = libraries.collect {
                def lib = Mock(JvmLibrarySpec)
                lib.name >> it
                def binaries = Mock(ModelMap)
                binaries.values() >> {[]}
                lib.binaries >> binaries
                lib
            }
            map.values() >> librarySpecs
            map.get(_) >> { args ->
                librarySpecs.find { it.name == args[0] }
            }
            map
            components.withType(_) >> map
        }
    }
}
