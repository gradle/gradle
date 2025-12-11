/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.plugins.BindsProjectFeature
import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.ProjectFeatureApplyAction
import org.gradle.api.internal.plugins.ProjectFeatureBinding
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilderInternal
import org.gradle.api.internal.plugins.ProjectTypeApplyAction
import org.gradle.api.internal.plugins.ProjectTypeBinding
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.InternalProblem
import org.gradle.api.problems.internal.InternalProblemReporter
import org.gradle.internal.properties.annotations.TypeMetadata
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata
import spock.lang.Specification

class DefaultProjectFeatureDeclarationsTest extends Specification {
    def metadataStore = Mock(TypeMetadataStore)
    def inspectionScheme = Mock(InspectionScheme)
    def problemReporter = Mock(InternalProblemReporter)
    def instantiator = Mock(Instantiator)
    def declarations = new DefaultProjectFeatureDeclarations(inspectionScheme, instantiator, problemReporter)
    def pluginId = "com.example.test"
    def bindsProjectTypeAnnotation = Mock(BindsProjectType)
    def bindsProjectFeatureAnnotation = Mock(BindsProjectFeature)
    def featureBinding = Mock(ProjectFeatureBinding)
    def typeBinding = Mock(ProjectTypeBinding)

    def "can declare and retrieve a project feature (implementation type = #className)"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)

        and:
        def implementations = declarations.projectFeatureImplementations.values()

        then:
        1 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.of(bindsProjectFeatureAnnotation)
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * bindsProjectFeatureAnnotation.value() >> Binding
        1 * instantiator.newInstance(Binding) >> featureBinding
        1 * featureBinding.bind(_) >> { args ->
            def builder = args[0] as ProjectFeatureBindingBuilderInternal
            builder.bindProjectFeatureToDefinition("test", TestDefinition, ParentDefinition, Mock(ProjectFeatureApplyAction))
                .withUnsafeDefinitionImplementationType(definitionImplementationType)
        }

        and:
        implementations.size() == 1
        implementations[0].definitionPublicType == TestDefinition
        implementations[0].definitionImplementationType == definitionImplementationType ?: TestDefinition
        implementations[0].featureName == "test"

        where:
        definitionImplementationType << [TestDefinitionImpl, null]
        className = definitionImplementationType?.simpleName
    }

    def "can declare and retrieve a project type (implementation type = #className)"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)

        and:
        def implementations = declarations.projectFeatureImplementations.values()

        then:
        1 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.of(bindsProjectTypeAnnotation)
        1 * bindsProjectTypeAnnotation.value() >> Binding
        1 * instantiator.newInstance(Binding) >> typeBinding
        1 * typeBinding.bind(_) >> { args ->
            def builder = args[0] as ProjectTypeBindingBuilder
            builder.bindProjectType("test", TestDefinition, Mock(ProjectTypeApplyAction))
                .withUnsafeDefinitionImplementationType(definitionImplementationType)
        }

        and:
        implementations.size() == 1
        implementations[0].definitionPublicType == TestDefinition
        implementations[0].definitionImplementationType == definitionImplementationType ?: TestDefinition
        implementations[0].featureName == "test"

        where:
        definitionImplementationType << [TestDefinitionImpl, null]
        className = definitionImplementationType?.simpleName
    }

    def "cannot declare a plugin that does not bind a project feature"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, NotAProjectTypeImpl, DeclaringPlugin)
        def implementations = declarations.projectFeatureImplementations

        then:
        1 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(NotAProjectTypeImpl) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()

        and:
        implementations.isEmpty()
    }

    def "registering the same plugin twice does not add two implementations"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def pluginTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)
        def definitionTypeMetadata = Mock(TypeMetadata)
        def definitionTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        def implementations = declarations.projectFeatureImplementations

        then:
        _ * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> pluginTypeAnnotationMetadata
        1 * pluginTypeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.of(bindsProjectFeatureAnnotation)
        1 * pluginTypeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * bindsProjectFeatureAnnotation.value() >> Binding
        1 * instantiator.newInstance(Binding) >> featureBinding
        1 * featureBinding.bind(_) >> { args ->
            def builder = args[0] as ProjectFeatureBindingBuilderInternal
            builder.bindProjectFeatureToDefinition("test", TestDefinition, ParentDefinition, Mock(ProjectFeatureApplyAction))
        }
        1 * metadataStore.getTypeMetadata(TestDefinition) >> definitionTypeMetadata
        1 * definitionTypeMetadata.getTypeAnnotationMetadata() >> definitionTypeAnnotationMetadata
        1 * definitionTypeAnnotationMetadata.getPropertiesAnnotationMetadata() >> ImmutableSortedSet.of()

        and:
        implementations.size() == 1
    }

    def "cannot declare two plugins with the same feature name"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def duplicatePluginTypeMetadata = Mock(TypeMetadata)
        def pluginTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)
        def duplicatePluginTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)
        def definitionTypeMetadata = Mock(TypeMetadata)
        def definitionTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        declarations.addDeclaration(pluginId+".duplicate", DuplicateProjectTypeImpl, DeclaringPlugin)
        declarations.getProjectFeatureImplementations()

        then:
        _ * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        1 * metadataStore.getTypeMetadata(DuplicateProjectTypeImpl) >> duplicatePluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> pluginTypeAnnotationMetadata
        1 * duplicatePluginTypeMetadata.getTypeAnnotationMetadata() >> duplicatePluginTypeAnnotationMetadata

        1 * pluginTypeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.of(bindsProjectFeatureAnnotation)
        1 * pluginTypeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * bindsProjectFeatureAnnotation.value() >> Binding
        1 * instantiator.newInstance(Binding) >> featureBinding
        1 * featureBinding.bind(_) >> { args ->
            def builder = args[0] as ProjectFeatureBindingBuilderInternal
            builder.bindProjectFeatureToDefinition("test", TestDefinition, ParentBuildModel, Mock(ProjectFeatureApplyAction))
        }
        1 * metadataStore.getTypeMetadata(TestDefinition) >> definitionTypeMetadata
        1 * definitionTypeMetadata.getTypeAnnotationMetadata() >> definitionTypeAnnotationMetadata
        1 * definitionTypeAnnotationMetadata.getPropertiesAnnotationMetadata() >> ImmutableSortedSet.of()

        1 * duplicatePluginTypeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.of(bindsProjectFeatureAnnotation)
        1 * duplicatePluginTypeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * bindsProjectFeatureAnnotation.value() >> Binding
        1 * instantiator.newInstance(Binding) >> featureBinding
        1 * featureBinding.bind(_) >> { args ->
            def builder = args[0] as ProjectFeatureBindingBuilderInternal
            builder.bindProjectFeatureToDefinition("test", TestDefinition, ParentBuildModel, Mock(ProjectFeatureApplyAction))
        }
        1 * problemReporter.internalCreate(_) >> Stub(InternalProblem) {
            getDefinition() >> Stub(ProblemDefinition) {
                getSeverity() >> Severity.ERROR
            }
        }

        and:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Project feature 'test' is registered by multiple plugins")
    }

    private interface ParentDefinition extends Definition<ParentBuildModel> { }

    private interface ParentBuildModel extends BuildModel { }

    private interface TestDefinition extends Definition<TestModel> { }

    private interface TestDefinitionImpl extends TestDefinition {}

    private interface TestModel extends BuildModel { }

    private abstract static class DeclaringPlugin implements Plugin<Settings> { }

    private abstract static class ProjectTypeImpl implements Plugin<Project> { }

    private abstract static class NotAProjectTypeImpl implements Plugin<Project> { }

    private abstract static class DuplicateProjectTypeImpl implements Plugin<Project> { }

    private abstract static class Binding implements ProjectFeatureBinding { }
}
