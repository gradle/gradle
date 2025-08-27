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
import org.gradle.api.internal.plugins.software.RegistersSoftwareFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.properties.annotations.PropertyMetadata
import org.gradle.internal.properties.annotations.TypeMetadata
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata
import org.gradle.plugin.software.internal.SoftwareFeatureRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

class SoftwareFeatureRegistrationPluginTargetTest extends Specification {
    def delegate = Mock(PluginTarget)
    def softwareFeatureRegistry = Mock(SoftwareFeatureRegistry)
    def inspectionScheme = Mock(InspectionScheme)
    def problems = TestUtil.problemsService()
    def pluginTarget = new SoftwareFeatureRegistrationPluginTarget(delegate, softwareFeatureRegistry, inspectionScheme, problems)
    def registrationPlugin = Mock(Plugin)
    def metadataStore = Mock(TypeMetadataStore)
    def registrationPluginTypeMetadata = Mock(TypeMetadata)
    def registrationTypeAnnotationMetadata = Mock(TypeAnnotationMetadata)
    def registersSoftwareTypes = Mock(RegistersSoftwareTypes)
    def registersSoftwareFeatures = Mock(RegistersSoftwareFeatures)
    def softwareTypePluginMetadata = Mock(TypeMetadata)
    def softwareTypePluginAnnotationMetadata = Mock(TypeAnnotationMetadata)
    def softwareFeaturePluginMetadata = Mock(TypeMetadata)
    def softwareFeaturePluginAnnotationMetadata = Mock(TypeAnnotationMetadata)
    def propertyMetadata = Mock(PropertyMetadata)

    def "adds software feature plugins for ecosystem plugin that registers #type"() {
        when:
        pluginTarget.applyImperative("com.example.test", registrationPlugin)

        then: // setup property metadata
        _ * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        2 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata

        and: // setup expectations for software type registration
        if (hasSoftwareTypes) {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
            1 * registersSoftwareTypes.value() >> [SoftwareTypePlugin.class]
            1 * metadataStore.getTypeMetadata(SoftwareTypePlugin.class) >> softwareTypePluginMetadata
            1 * softwareTypePluginMetadata.getTypeAnnotationMetadata() >> softwareTypePluginAnnotationMetadata
            1 * softwareTypePluginAnnotationMetadata.getAnnotation(BindsSoftwareType.class) >> Optional.of(Stub(BindsSoftwareType))
            1 * softwareFeatureRegistry.register("com.example.test", SoftwareTypePlugin.class, null)
        } else {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.empty()
        }
        _ * softwareTypePluginMetadata.getPropertiesMetadata() >> []

        and: // setup expectations for software feature registration
        if (hasSoftwareFeatures) {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareFeatures.class) >> Optional.of(registersSoftwareFeatures)
            1 * registersSoftwareFeatures.value() >> [SoftwareFeaturePlugin.class]
            1 * metadataStore.getTypeMetadata(SoftwareFeaturePlugin.class) >> softwareFeaturePluginMetadata
            2 * softwareFeaturePluginMetadata.getTypeAnnotationMetadata() >> softwareFeaturePluginAnnotationMetadata
            1 * softwareFeaturePluginAnnotationMetadata.getAnnotation(BindsSoftwareType.class) >> Optional.empty()
            1 * softwareFeaturePluginAnnotationMetadata.getAnnotation(BindsSoftwareFeature.class) >> Optional.of(Stub(BindsSoftwareFeature))
            1 * softwareFeatureRegistry.register("com.example.test", SoftwareFeaturePlugin.class, null)
        } else {
            1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareFeatures.class) >> Optional.empty()
        }
        _ * softwareFeaturePluginMetadata.getPropertiesMetadata() >> []

        and:
        1 * delegate.applyImperative("com.example.test", registrationPlugin)

        where:
        type                                | hasSoftwareTypes | hasSoftwareFeatures
        "both software types and features"  | true            | true
        "only software types"               | true            | false
        "only software features"            | false           | true
    }

    def "adds software type plugins for plugin that registers software types using SoftwareType annotation"() {
        when:
        pluginTarget.applyImperative("com.example.test", registrationPlugin)

        then: // setup property metadata
        _ * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        2 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareFeatures.class) >> Optional.empty()
        1 * registersSoftwareTypes.value() >> [SoftwareTypePlugin.class]
        1 * metadataStore.getTypeMetadata(SoftwareTypePlugin.class) >> softwareTypePluginMetadata
        1 * softwareTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata]
        2 * softwareTypePluginMetadata.getTypeAnnotationMetadata() >> softwareTypePluginAnnotationMetadata
        1 * softwareTypePluginAnnotationMetadata.getAnnotation(BindsSoftwareType.class) >> Optional.empty()
        1 * softwareTypePluginAnnotationMetadata.getAnnotation(BindsSoftwareFeature.class) >> Optional.empty()

        and: // returns property metadata with an annotation
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(Stub(SoftwareType))
        1 * softwareFeatureRegistry.register("com.example.test", SoftwareTypePlugin.class, null)

        and:
        1 * delegate.applyImperative("com.example.test", registrationPlugin)
    }

    def "throws exception when plugins are registered that do not expose software types"() {
        when:
        pluginTarget.applyImperative(null, registrationPlugin)

        then: // setup property metadata
        2 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        1 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata
        1 * registrationPluginTypeMetadata.getType() >> registrationPlugin.class
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.of(registersSoftwareTypes)
        1 * registersSoftwareTypes.value() >> [SoftwareTypePlugin.class]
        1 * metadataStore.getTypeMetadata(SoftwareTypePlugin.class) >> softwareTypePluginMetadata
        1 * softwareTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata]
        2 * softwareTypePluginMetadata.getTypeAnnotationMetadata() >> softwareTypePluginAnnotationMetadata
        1 * softwareTypePluginAnnotationMetadata.getAnnotation(BindsSoftwareType.class) >> Optional.empty()
        1 * softwareTypePluginAnnotationMetadata.getAnnotation(BindsSoftwareFeature.class) >> Optional.empty()

        and: // returns metadata with no annotation present
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.empty()

        and:
        def e = thrown(DefaultMultiCauseException)
        e.hasCause(InvalidUserDataException)
    }

    def "throws exception when plugins are registered that expose multiple software types"() {
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
        1 * registersSoftwareTypes.value() >> [SoftwareTypePlugin.class]
        1 * metadataStore.getTypeMetadata(SoftwareTypePlugin.class) >> softwareTypePluginMetadata
        1 * softwareTypePluginMetadata.getPropertiesMetadata() >> [propertyMetadata, anotherPropertyMetadata]
        2 * softwareTypePluginMetadata.getTypeAnnotationMetadata() >> softwareTypePluginAnnotationMetadata
        1 * softwareTypePluginAnnotationMetadata.getAnnotation(BindsSoftwareType.class) >> Optional.empty()
        1 * softwareTypePluginAnnotationMetadata.getAnnotation(BindsSoftwareFeature.class) >> Optional.empty()

        and: // returns multiple properties with annotation present
        1 * propertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(Stub(SoftwareType))
        1 * anotherPropertyMetadata.getAnnotation(SoftwareType.class) >> Optional.of(Stub(SoftwareType))

        and:
        def e = thrown(DefaultMultiCauseException)
        e.hasCause(InvalidUserDataException)
    }

    def "calls delegate for plugins that do not register software types"() {
        when:
        pluginTarget.applyImperative(null, registrationPlugin)

        then:
        1 * inspectionScheme.getMetadataStore() >> metadataStore
        1 * metadataStore.getTypeMetadata(registrationPlugin.class) >> registrationPluginTypeMetadata
        2 * registrationPluginTypeMetadata.getTypeAnnotationMetadata() >> registrationTypeAnnotationMetadata
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareTypes.class) >> Optional.empty()
        1 * registrationTypeAnnotationMetadata.getAnnotation(RegistersSoftwareFeatures.class) >> Optional.empty()

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

    abstract class SoftwareTypePlugin implements Plugin<Project> { }
    abstract class SoftwareFeaturePlugin implements Plugin<Project> { }
    private static class Rule {}
}
