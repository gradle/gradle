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

import com.google.common.reflect.TypeToken
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.plugins.BindsProjectFeature
import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.internal.properties.annotations.PropertyMetadata
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

    def "can declare and retrieve a project type (public type = #modelPublicType.simpleName)"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def modelTypeMetadata = Mock(TypeMetadata)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)
        def propertyMetadata = Mock(PropertyMetadata)
        def softwareType = Mock(SoftwareType)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)

        and:
        def implementations = declarations.projectFeatureImplementations.values()

        then:
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        2 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * pluginTypeMetadata.getPropertiesMetadata() >> [propertyMetadata]
        1 * propertyMetadata.getPropertyType() >> SoftwareType.class
        (1..2) * propertyMetadata.getDeclaredType() >> TypeToken.of(TestModel.class)
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(softwareType)
        _ * softwareType.name() >> "test"
        (1..2) * softwareType.modelPublicType() >> modelPublicType
        1 * metadataStore.getTypeMetadata(TestModel) >> modelTypeMetadata
        1 * modelTypeMetadata.getPropertiesMetadata() >> []

        and:
        implementations.size() == 1
        implementations[0].definitionPublicType == TestModel
        implementations[0].featureName == "test"

        where:
        modelPublicType << [TestModel, Void]
    }

    def "cannot declare a plugin that is not a project type"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, NotAProjectTypeImpl, DeclaringPlugin)
        def implementations = declarations.projectFeatureImplementations

        then:
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        2 * metadataStore.getTypeMetadata(NotAProjectTypeImpl) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * pluginTypeMetadata.getPropertiesMetadata() >> []

        and:
        implementations.isEmpty()
    }

    def "registering the same plugin twice does not add two implementations"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def modelTypeMetadata = Mock(TypeMetadata)
        def propertyMetadata = Mock(PropertyMetadata)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)
        def softwareType = Mock(SoftwareType)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        def implementations = declarations.projectFeatureImplementations

        then:
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        2 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * pluginTypeMetadata.getPropertiesMetadata() >> [propertyMetadata]
        1 * propertyMetadata.getPropertyType() >> SoftwareType.class
        1 * propertyMetadata.getDeclaredType() >> TypeToken.of(TestModel.class)
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(softwareType)
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        _ * softwareType.name() >> "test"
        2 * softwareType.modelPublicType() >> TestModel
        1 * metadataStore.getTypeMetadata(TestModel) >> modelTypeMetadata
        1 * modelTypeMetadata.getPropertiesMetadata() >> []

        and:
        implementations.size() == 1
    }

    def "cannot declare two plugins with the same project type"() {
        def pluginTypeMetadata = Mock(TypeMetadata)
        def duplicateTypeMetadata = Mock(TypeMetadata)
        def modelTypeMetadata = Mock(TypeMetadata)
        def propertyMetadata = Mock(PropertyMetadata)
        def duplicatePropertyMetadata = Mock(PropertyMetadata)
        def softwareType = Mock(SoftwareType)
        def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)
        def duplicateTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)

        when:
        declarations.addDeclaration(pluginId, ProjectTypeImpl, DeclaringPlugin)
        declarations.addDeclaration(pluginId+".duplicate", DuplicateProjectTypeImpl, DeclaringPlugin)
        declarations.getProjectFeatureImplementations()

        then:
        4 * inspectionScheme.getMetadataStore() >> metadataStore
        2 * metadataStore.getTypeMetadata(ProjectTypeImpl) >> pluginTypeMetadata
        2 * metadataStore.getTypeMetadata(DuplicateProjectTypeImpl) >> duplicateTypeMetadata
        1 * pluginTypeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * duplicateTypeMetadata.getTypeAnnotationMetadata() >> duplicateTypeAnnotationMetadata
        1 * pluginTypeMetadata.getPropertiesMetadata() >> [propertyMetadata]
        1 * duplicateTypeMetadata.getPropertiesMetadata() >> [duplicatePropertyMetadata]
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()
        1 * typeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * duplicateTypeAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()
        1 * duplicateTypeAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * propertyMetadata.getPropertyType() >> SoftwareType.class
        1 * propertyMetadata.getDeclaredType() >> TypeToken.of(TestModel.class)
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(softwareType)
        1 * duplicatePropertyMetadata.getPropertyType() >> SoftwareType.class
        1 * duplicatePropertyMetadata.getDeclaredType() >> TypeToken.of(TestModel.class)
        1 * duplicatePropertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(softwareType)
        _ * softwareType.name() >> "test"
        2 * softwareType.modelPublicType() >> TestModel
        2 * metadataStore.getTypeMetadata(TestModel) >> modelTypeMetadata
        1 * modelTypeMetadata.getPropertiesMetadata() >> []

        and:
        def e = thrown(IllegalArgumentException)
        e.message == "Project type 'test' is registered by both '${this.class.name}\$DuplicateProjectTypeImpl' and '${this.class.name}\$ProjectTypeImpl'"
    }

    private static class TestModel { }

    private abstract static class DeclaringPlugin implements Plugin<Settings> { }

    private abstract static class ProjectTypeImpl implements Plugin<Project> { }

    private abstract static class NotAProjectTypeImpl implements Plugin<Project> { }

    private abstract static class DuplicateProjectTypeImpl implements Plugin<Project> { }
}
