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

package org.gradle.api.internal.plugins

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.software.RegistersProjectFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.properties.annotations.PropertyMetadata
import org.gradle.internal.properties.annotations.TypeMetadata
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata
import org.gradle.plugin.software.internal.ProjectFeatureDeclarations
import org.gradle.util.TestUtil
import spock.lang.Specification

class ProjectFeatureDeclarationPluginTargetTest extends Specification {
    def delegate = Mock(PluginTarget)
    def projectFeatureDeclarations = Mock(ProjectFeatureDeclarations)
    def inspectionScheme = Mock(InspectionScheme)
    def problems = TestUtil.problemsService()
    def pluginTarget = new ProjectFeatureDeclarationPluginTarget(delegate, projectFeatureDeclarations, inspectionScheme, problems)
    def registrationPlugin = Mock(Plugin)
    def metadataStore = Mock(TypeMetadataStore)
    def registrationPluginTypeMetadata = Mock(TypeMetadata)
    def registrationTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)
    def registersSoftwareTypes = Mock(RegistersSoftwareTypes)
    def registersProjectFeatures = Mock(RegistersProjectFeatures)
    def projectTypePluginMetadata = Mock(TypeMetadata)
    def projectTypePluginAnnotationMetadata = Mock(TypeAnnotationMetadata)
    def projectFeaturePluginMetadata = Mock(TypeMetadata)
    def projectFeaturePluginAnnotationMetadata = Mock(TypeAnnotationMetadata)
    def propertyMetadata = Mock(PropertyMetadata)

    def "adds project feature plugins for ecosystem plugin that declare #type"() {
        when:
        pluginTarget.applyImperative("com.example.test", registrationPlugin)

        then: // setup property metadata
        _ * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        2 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata

        and: // setup expectations for project type registration
        if (hasProjectTypes) {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
            1 * registersSoftwareTypes.value() >> [ProjectTypePlugin.class]
            1 * metadataStore.getTypeMetadata(ProjectTypePlugin.class) >> projectTypePluginMetadata
            1 * projectTypePluginMetadata.getTypeAnnotationMetadata() >> projectTypePluginAnnotationMetadata
            1 * projectTypePluginAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.of(Stub(BindsProjectType))
            1 * projectFeatureDeclarations.addDeclaration("com.example.test", ProjectTypePlugin.class, null)
        } else {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.empty()
        }
        _ * projectTypePluginMetadata.getPropertiesMetadata() >> []

        and: // setup expectations for project feature registration
        if (hasProjectFeatures) {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersProjectFeatures.class) >> Optional.of(registersProjectFeatures)
            1 * registersProjectFeatures.value() >> [ProjectFeaturePlugin.class]
            1 * metadataStore.getTypeMetadata(ProjectFeaturePlugin.class) >> projectFeaturePluginMetadata
            2 * projectFeaturePluginMetadata.getTypeAnnotationMetadata() >> projectFeaturePluginAnnotationMetadata
            1 * projectFeaturePluginAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
            1 * projectFeaturePluginAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.of(Stub(BindsProjectFeature))
            1 * projectFeatureDeclarations.addDeclaration("com.example.test", ProjectFeaturePlugin.class, null)
        } else {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersProjectFeatures.class) >> Optional.empty()
        }
        _ * projectFeaturePluginMetadata.getPropertiesMetadata() >> []

        and:
        1 * delegate.applyImperative("com.example.test", registrationPlugin)

        where:
        type                                | hasProjectTypes | hasProjectFeatures
        "both project types and features"  | true            | true
        "only project types"               | true            | false
        "only project features"            | false           | true
    }

    def "adds project type plugins for plugin that declares project types using SoftwareType annotation"() {
        when:
        pluginTarget.applyImperative("com.example.test", registrationPlugin)

        then: // setup property metadata
        _ * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        2 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersProjectFeatures.class) >> Optional.empty()
        1 * registersSoftwareTypes.value() >> [ProjectTypePlugin.class]
        1 * metadataStore.getTypeMetadata(ProjectTypePlugin.class) >> projectTypePluginMetadata
        1 * projectTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata]
        2 * projectTypePluginMetadata.getTypeAnnotationMetadata() >> projectTypePluginAnnotationMetadata
        1 * projectTypePluginAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * projectTypePluginAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()

        and: // returns property metadata with an annotation
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(Stub(SoftwareType))
        1 * projectFeatureDeclarations.addDeclaration("com.example.test", ProjectTypePlugin.class, null)

        and:
        1 * delegate.applyImperative("com.example.test", registrationPlugin)
    }

    def "throws exception when plugins are declared that do not expose project types"() {
        when:
        pluginTarget.applyImperative(null, registrationPlugin)

        then: // setup property metadata
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        1 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata
        1 * registrationPluginTypeMetadata.getType() >> registrationPlugin.class
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
        1 * registersSoftwareTypes.value() >> [ProjectTypePlugin.class]
        1 * metadataStore.getTypeMetadata(ProjectTypePlugin.class) >> projectTypePluginMetadata
        1 * projectTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata]
        2 * projectTypePluginMetadata.getTypeAnnotationMetadata() >> projectTypePluginAnnotationMetadata
        1 * projectTypePluginAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * projectTypePluginAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()

        and: // returns metadata with no annotation present
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.empty()

        and:
        def e = thrown(DefaultMultiCauseException)
        e.hasCause(InvalidUserDataException)
    }

    def "throws exception when plugins are declared that expose multiple project types"() {
        given:
        def anotherPropertyMetadata = Mock(PropertyMetadata)

        when:
        pluginTarget.applyImperative(null, registrationPlugin)

        then: // setup property metadata
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        1 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata
        1 * registrationPluginTypeMetadata.getType() >> registrationPlugin.class
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
        1 * registersSoftwareTypes.value() >> [ProjectTypePlugin.class]
        1 * metadataStore.getTypeMetadata(ProjectTypePlugin.class) >> projectTypePluginMetadata
        1 * projectTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata, anotherPropertyMetadata]
        2 * projectTypePluginMetadata.getTypeAnnotationMetadata() >> projectTypePluginAnnotationMetadata
        1 * projectTypePluginAnnotationMetadata.getAnnotation(BindsProjectType.class) >> Optional.empty()
        1 * projectTypePluginAnnotationMetadata.getAnnotation(BindsProjectFeature.class) >> Optional.empty()

        and: // returns multiple properties with annotation present
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(Stub(SoftwareType))
        1 * anotherPropertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(Stub(SoftwareType))

        and:
        def e = thrown(DefaultMultiCauseException)
        e.hasCause(InvalidUserDataException)
    }

    def "calls delegate for plugins that do not declare project types"() {
        when:
        pluginTarget.applyImperative(null, registrationPlugin)

        then:
        1 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        2 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.empty()
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersProjectFeatures.class) >> Optional.empty()

        and:
        1 * delegate.applyImperative(null, registrationPlugin)
    }

    def "passes rule targets to delegate only"() {
        when:
        pluginTarget.applyRules(null, Rule.class)

        then:
        1 * delegate.applyRules(null, Rule.class)
        0 * _
    }

    abstract class ProjectTypePlugin implements Plugin<Project> { }
    abstract class ProjectFeaturePlugin implements Plugin<Project> { }
    private static class Rule {}
}
