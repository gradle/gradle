/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.jvm.internal.resolve

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.resolve.DefaultLocalLibraryResolver
import org.gradle.api.internal.resolve.DefaultProjectModelResolver
import org.gradle.api.internal.resolve.LocalLibraryDependencyResolver
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.api.internal.resolve.VariantBinarySelector
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult
import org.gradle.jvm.JarBinarySpec
import org.gradle.jvm.JvmBinarySpec
import org.gradle.jvm.JvmLibrarySpec
import org.gradle.jvm.internal.DefaultJarFile
import org.gradle.jvm.internal.JarBinarySpecInternal
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaExtractor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.LibrarySpec
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.platform.base.internal.DefaultDependencySpecContainer
import org.gradle.platform.base.internal.VariantAspectExtractionStrategy
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.WrapUtil.toDomainObjectSet

class JvmLocalLibraryDependencyResolverTest extends Specification {

    public static final ExcludeSpec NOTHING = new ModuleExclusions().nothing()

    Map<String, Project> projects
    ProjectRegistry<ProjectInternal> projectRegistry
    ProjectModelResolver projectModelResolver
    Project rootProject
    LocalLibraryDependencyResolver resolver
    DependencyMetadata metadata
    LibraryComponentSelector selector
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
        variants.nonNullVariantAxes >> ['targetPlatform']
        variants.declaredVariantAxes >> ['targetPlatform']
        variants.getVariantAxisType(_) >> ModelType.of(JavaPlatform)
        def schemaStore = new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies([], new ModelSchemaAspectExtractor([new VariantAspectExtractionStrategy()])))
        def libraryAdapter = new JvmLocalLibraryMetaDataAdapter()
        def errorMessageBuilder = new DefaultLibraryResolutionErrorMessageBuilder(variants, schemaStore)
        def variantDimensionSelectorFactories = [DefaultVariantAxisCompatibilityFactory.of(JavaPlatform, new DefaultJavaPlatformVariantAxisCompatibility())]
        VariantBinarySelector variantSelector = new JvmVariantSelector(variantDimensionSelectorFactories, JvmBinarySpec.class, schemaStore, variants);

        resolver = new LocalLibraryDependencyResolver(JarBinarySpec, projectModelResolver, new DefaultLocalLibraryResolver(), variantSelector, libraryAdapter, errorMessageBuilder)
        metadata = Mock(DependencyMetadata)
        selector = Mock(LibraryComponentSelector)
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
        selector.projectPath >> projectPath
        selector.libraryName >> lib
        mockLibraries(rootProject, rootProjectComponents)

        subprojects.each { name, libs ->
            def pj = mockProject(":$name")
            mockLibraries(pj, libs)
        }


        def result = new DefaultBuildableComponentIdResolveResult()

        when:
        resolver.resolve(metadata, null, null, result)

        then:
        result.hasResult()
        if (failure) {
            assert result.failure.cause.message =~ failure
        } else {
            assert result.failure == null
            def md = result.metadata
            assert md.moduleVersionId.group == selector.projectPath
            if (selector.libraryName) {
                assert md.moduleVersionId.name == selector.libraryName
            } else {
                assert md.moduleVersionId.name.length() > 0
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
        def artifact = Mock(ComponentArtifactMetadata)
        def result = new DefaultBuildableArtifactResolveResult()
        artifact.componentId >> Mock(LibraryBinaryIdentifier)

        when:
        resolver.resolveArtifact(artifact, ImmutableModuleSources.of(), result)

        then:
        result.hasResult()
    }

    def "ignores non library artifacts"() {
        given:
        def artifact = Mock(ComponentArtifactMetadata)
        def result = new DefaultBuildableArtifactResolveResult()
        artifact.componentId >> Mock(ModuleComponentIdentifier)

        when:
        resolver.resolveArtifact(artifact, ImmutableModuleSources.of(), result)

        then:
        !result.hasResult()
    }

    def "handles library module artifacts"() {
        given:
        def component = Mock(ComponentResolveMetadata)
        def configuration = Mock(ConfigurationMetadata)
        def result = new DefaultBuildableArtifactSetResolveResult()
        component.id >> Mock(LibraryBinaryIdentifier)
        configuration.variants >> ([] as Set)

        when:
        resolver.resolveArtifactsWithType(component, ArtifactType.SOURCES, result)

        then:
        result.hasResult()

        when:
        def artifacts = resolver.resolveArtifacts(component, configuration, Stub(ArtifactTypeRegistry), NOTHING, ImmutableAttributes.EMPTY)

        then:
        artifacts != null
    }

    def "ignores non library module artifacts"() {
        given:
        def component = Mock(ComponentResolveMetadata)
        def configuration = Mock(ConfigurationMetadata)
        def result = new DefaultBuildableArtifactSetResolveResult()
        component.id >> Mock(ModuleComponentIdentifier)

        when:
        resolver.resolveArtifactsWithType(component, ArtifactType.SOURCES, result)

        then:
        !result.hasResult()

        when:
        def artifacts = resolver.resolveArtifacts(component, configuration, Stub(ArtifactTypeRegistry), NOTHING, ImmutableAttributes.EMPTY)

        then:
        artifacts == null
    }

    private ModelMap<? extends LibrarySpec> mockLibraries(Project project, List<String> libraries) {
        if (libraries == null) {
            project.modelRegistry.find(_, _) >> null
        } else {
            ComponentSpecContainer components = Mock()
            project.modelRegistry.find(_, _) >> components
            def map = Mock(ModelMap)
            def sources = Mock(ModelMap)
            sources.values() >> []
            def librarySpecs = libraries.collect { library ->
                def lib = Mock(JvmLibrarySpec)
                lib.name >> library
                lib.dependencies >> new DefaultDependencySpecContainer()
                lib.sources >> sources

                def apiJar = newDefaultJarFile("apiJar")
                apiJar.setFile(new File("api.jar"))
                def runtimeJar = newDefaultJarFile("runtimeJar")
                runtimeJar.setFile(new File('runtime.jar'))

                def binary = Mock(JarBinarySpecInternal)
                binary.publicType >> JarBinarySpec
                binary.id >> new DefaultLibraryBinaryIdentifier(project.path, library, 'foo')
                binary.displayName >> "binary for $lib"
                binary.name >> 'foo'
                binary.buildTask >> Mock(Task)
                binary.targetPlatform >> platform
                binary.jarFile >> runtimeJar.file
                binary.apiJarFile >> apiJar.file
                binary.apiJar >> apiJar
                binary.runtimeJar >> runtimeJar
                binary.apiDependencies >> []
                binary.dependencies >> []
                binary.library >> lib
                binary.inputs >> toDomainObjectSet(LanguageSourceSet)
                binary.sources >> sources
                lib.variants >> [binary]
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

    private DefaultJarFile newDefaultJarFile(String componentName) {
        new DefaultJarFile(new DefaultComponentSpecIdentifier(":", componentName))
    }
}
