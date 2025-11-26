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
import org.gradle.internal.properties.annotations.TypeMetadata
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata
import spock.lang.Specification

class DefaultProjectFeatureDeclarationsTest extends Specification {
    def metadataStore = Mock(TypeMetadataStore)
    def inspectionScheme = Mock(InspectionScheme)
    def instantiator = Mock(Instantiator)
    def declarations = new DefaultProjectFeatureDeclarations(inspectionScheme, instantiator)
    def pluginId = "com.example.test"
    def bindsProjectTypeAnnotation = Mock(BindsProjectType)
    def bindsProjectFeatureAnnotation = Mock(BindsProjectFeature)
    def featureBinding = Mock(ProjectFeatureBinding)
    def typeBinding = Mock(ProjectTypeBinding)

    def "can declare and retrieve a project feature (implementation type = #definitionImplementationType?.simpleName)"() {
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
                .withDefinitionImplementationType(definitionImplementationType)
        }

        and:
        implementations.size() == 1
        implementations[0].definitionPublicType == TestDefinition
        implementations[0].definitionImplementationType == definitionImplementationType ?: TestDefinition
        implementations[0].featureName == "test"

        where:
        definitionImplementationType << [TestDefinitionImpl, null]
    }

    def "can declare and retrieve a project type (implementation type = #definitionImplementationType?.simpleName)"() {
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
                .withDefinitionImplementationType(definitionImplementationType)
        }

        and:
        implementations.size() == 1
        implementations[0].definitionPublicType == TestDefinition
        implementations[0].definitionImplementationType == definitionImplementationType ?: TestDefinition
        implementations[0].featureName == "test"

        where:
        definitionImplementationType << [TestDefinitionImpl, null]
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
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        def implementations = declarations.projectFeatureImplementations

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
        }

        and:
        implementations.size() == 1
    }

    def "cannot declare two plugins with the same project type"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def duplicateTypeMetadata = Mock(TypeMetadata)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)
        def duplicateTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        declarations.addDeclaration(pluginId+".duplicate", DuplicateProjectTypeImpl, DeclaringPlugin)
        declarations.getProjectFeatureImplementations()

        then:
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        1 * metadataStore.getTypeMetadata(DuplicateProjectTypeImpl) >> duplicateTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * duplicateTypeMetadata.getTypeAnnotationMetadata() >> duplicateTypeAnnotationMetadata

        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.of(bindsProjectFeatureAnnotation)
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * bindsProjectFeatureAnnotation.value() >> Binding
        1 * instantiator.newInstance(Binding) >> featureBinding
        1 * featureBinding.bind(_) >> { args ->
            def builder = args[0] as ProjectFeatureBindingBuilderInternal
            builder.bindProjectFeatureToDefinition("test", TestDefinition, ParentBuildModel, Mock(ProjectFeatureApplyAction))
        }

        1 * duplicateTypeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.of(bindsProjectFeatureAnnotation)
        1 * duplicateTypeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * bindsProjectFeatureAnnotation.value() >> Binding
        1 * instantiator.newInstance(Binding) >> featureBinding
        1 * featureBinding.bind(_) >> { args ->
            def builder = args[0] as ProjectFeatureBindingBuilderInternal
            builder.bindProjectFeatureToDefinition("test", TestDefinition, ParentBuildModel, Mock(ProjectFeatureApplyAction))
        }

        and:
        def e = thrown(IllegalArgumentException)
        e.message == "Project feature 'test' is registered by both '${this.class.name}\$DuplicateProjectTypeImpl' and '${this.class.name}\$ProjectTypeImpl'"
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
