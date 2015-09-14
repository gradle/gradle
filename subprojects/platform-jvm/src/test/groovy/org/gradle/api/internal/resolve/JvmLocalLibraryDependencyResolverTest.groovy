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
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier
import org.gradle.internal.component.model.*
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult
import org.gradle.jvm.JarBinarySpec
import org.gradle.jvm.JvmLibrarySpec
import org.gradle.jvm.internal.DefaultJavaPlatformVariantDimensionSelector
import org.gradle.jvm.internal.JarBinarySpecInternal
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.language.base.internal.model.DefaultVariantDimensionSelectorFactory
import org.gradle.language.base.internal.model.VariantsMetaData
import org.gradle.model.ModelMap
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractor
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.LibrarySpec
import org.gradle.platform.base.internal.VariantAspectExtractionStrategy
import spock.lang.Specification
import spock.lang.Unroll

class JvmLocalLibraryDependencyResolverTest extends Specification {

    Map<String, Project> projects
    ProjectRegistry<ProjectInternal> projectRegistry
    ProjectModelResolver projectModelResolver
    Project rootProject
    JvmLocalLibraryDependencyResolver resolver
    DependencyMetaData metadata
    LibraryComponentSelector selector
    ModuleVersionSelector requested
    JavaPlatform platform

    def setup() {
        projects = [:]
        projectRegistry = Mock(ProjectRegistry)
        projectRegistry.getProject(_) >> {
            String name = it[0]
            projects[name]
        }
        projectModelResolver = new DefaultProjectModelResolver(projectRegistry)
        rootProject = mockProject(':')
        platform = DefaultJavaPlatform.current()
        def variants = Mock(VariantsMetaData)
        variants.getValueAsType(JavaPlatform, 'targetPlatform') >> platform
        variants.nonNullDimensions >> ['targetPlatform']
        variants.allDimensions >> ['targetPlatform']
        variants.getDimensionType(_) >> ModelType.of(JavaPlatform)
        def schemaStore = new DefaultModelSchemaStore(new ModelSchemaExtractor([], new ModelSchemaAspectExtractor([new VariantAspectExtractionStrategy()])))
        resolver = new JvmLocalLibraryDependencyResolver(projectModelResolver, variants, [DefaultVariantDimensionSelectorFactory.of(JavaPlatform, new DefaultJavaPlatformVariantDimensionSelector())], schemaStore)
        metadata = Mock(DependencyMetaData)
        selector = Mock(LibraryComponentSelector)
        requested = Mock(ModuleVersionSelector)
        metadata.requested >> requested
        metadata.selector >> selector

    }

    private ProjectInternal mockProject(String path) {
        def mock = Mock(ProjectInternal)
        mock.findProject(':') >> mock
        mock.path >> path
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
        null   | ':'         | ['lib']               | [:]                    | false
        'lib'  | ':'         | ['lib', 'lib2']       | [:]                    | false
        'lib2' | ':'         | ['lib', 'lib2']       | [:]                    | false
        'lib2' | ':'         | ['lib']               | [:]                    | "Did you want to use 'lib'"
        'lib2' | ':'         | ['lib', 'lib3']       | [:]                    | "Did you want to use one of 'lib', 'lib3'"
        null   | ':'         | ['lib', 'lib2']       | [:]                    | "Project ':' contains more than one library. Please select one of 'lib', 'lib2'"
        'lib'  | ':foo'      | ['lib']               | [:]                    | "Project ':foo' not found."
        'lib'  | ':'         | null                  | [:]                    | "Project ':' doesn't define any library"
        'lib'  | ':'         | []                    | [:]                    | "Project ':' doesn't define any library"
        'lib'  | ':foo'      | []                    | [foo: ['lib']]         | false
        'lib'  | ':foo'      | []                    | [foo: ['lib', 'lib2']] | false
        'lib'  | ':foo'      | []                    | [foo: ['lib2']]        | "Did you want to use 'lib2'"
        'lib2' | ':foo'      | []                    | [foo: ['lib', 'lib3']] | "Did you want to use one of 'lib', 'lib3'"
        null   | ':foo'      | []                    | [foo: ['lib', 'lib2']] | "Project ':foo' contains more than one library. Please select one of 'lib', 'lib2'"
        null   | ':foo'      | []                    | [foo: null]            | "Project ':foo' doesn't define any library"
        null   | ':foo'      | []                    | [foo: []]              | "Project ':foo' doesn't define any library"

    }

    def "handles library artifacts"() {
        given:
        def artifact = Mock(ComponentArtifactMetaData)
        def result = new DefaultBuildableArtifactResolveResult()
        artifact.componentId >> Mock(LibraryBinaryIdentifier)

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
        component.componentId >> Mock(LibraryBinaryIdentifier)

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
            def librarySpecs = libraries.collect { library ->
                def lib = Mock(JvmLibrarySpec)
                lib.name >> library
                def binaries = Mock(ModelMap)
                def binary = Mock(JarBinarySpecInternal)
                binary.publicType >> JarBinarySpec
                binary.id >> new DefaultLibraryBinaryIdentifier(project.path, library, 'api')
                binary.displayName >> "binary for $lib"
                binary.name >> 'api'
                binary.buildTask >> Mock(Task)
                binary.targetPlatform >> platform
                binary.jarFile >> new File("api.jar")
                def values = [binary]
                binaries.values() >> { values }
                binaries.withType(JarBinarySpec) >> {
                     values as ModelMap
                }
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
