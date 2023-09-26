/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.internal.mapping

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.publish.internal.component.ResolutionBackedVariant
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import javax.annotation.Nullable

/**
 * Tests {@link DefaultDependencyCoordinateResolverFactory}.
 */
class DefaultDependencyCoordinateResolverFactoryTest extends Specification {

    ProjectDependencyPublicationResolver projectDependencyResolver = Mock()
    ImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    AttributeDesugaring attributeDesugaring = Mock()

    def factory = new DefaultDependencyCoordinateResolverFactory(
        projectDependencyResolver, moduleIdentifierFactory, EmptySchema.INSTANCE, AttributeTestUtil.attributesFactory(), attributeDesugaring
    )

    def "returns project-only resolver when version mapping is disabled and variant is not resolution-backed"() {
        given:
        def variant = nonResolutionBacked()
        def versionMappingStrategy = versionMappingStrategy(false)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            componentResolver instanceof DefaultDependencyCoordinateResolverFactory.ProjectOnlyComponentDependencyResolver
            variantResolver.delegate == componentResolver
        }
    }

    def "returns project-only resolver when dependency mapping and version mapping are disabled"() {
        given:
        def variant = resolutionBacked(false, null)
        def versionMappingStrategy = versionMappingStrategy(false)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            componentResolver instanceof DefaultDependencyCoordinateResolverFactory.ProjectOnlyComponentDependencyResolver
            variantResolver.delegate == componentResolver
        }
    }

    def "returns project-only resolver if dependency version mapping is enabled without any configuration and variant is not resolution-backed"() {
        given:
        def variant = nonResolutionBacked()
        def versionMappingStrategy = versionMappingStrategy(true)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            componentResolver instanceof DefaultDependencyCoordinateResolverFactory.ProjectOnlyComponentDependencyResolver
            variantResolver.delegate == componentResolver
        }
    }

    def "returns legacy resolver if dependency version mapping is enabled with default configuration and variant is not resolution-backed"() {
        given:
        def conf = Mock(Configuration)
        def variant = nonResolutionBacked()
        def versionMappingStrategy = versionMappingStrategy(true, conf, null)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            componentResolver instanceof VersionMappingVariantDependencyResolver
            (componentResolver as VersionMappingVariantDependencyResolver).versionMappingConfiguration == conf
            variantResolver.delegate == componentResolver
        }
    }

    def "returns legacy resolver if dependency version mapping is enabled with explicit configuration and variant is not resolution-backed"() {
        given:
        def conf = Mock(Configuration)
        def variant = nonResolutionBacked()
        def versionMappingStrategy = versionMappingStrategy(true, null, conf)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            componentResolver instanceof VersionMappingVariantDependencyResolver
            (componentResolver as VersionMappingVariantDependencyResolver).versionMappingConfiguration == conf
            variantResolver.delegate == componentResolver
        }
    }

    def "returns legacy resolver if version mapping is enabled with default configuration and dependency mapping is disabled with resolution configuration"() {
        given:
        def dependencyMappingConf = Mock(Configuration)
        def versionMappingConf = Mock(Configuration)
        def variant = resolutionBacked(false, dependencyMappingConf)
        def versionMappingStrategy = versionMappingStrategy(true, versionMappingConf, null)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            componentResolver instanceof VersionMappingVariantDependencyResolver
            (componentResolver as VersionMappingVariantDependencyResolver).versionMappingConfiguration == dependencyMappingConf
            variantResolver.delegate == componentResolver
        }
    }

    def "returns legacy resolver if version mapping is enabled with user configuration and dependency mapping is disabled with resolution configuration"() {
        given:
        def dependencyMappingConf = Mock(Configuration)
        def versionMappingConf = Mock(Configuration)
        def variant = resolutionBacked(false, dependencyMappingConf)
        def versionMappingStrategy = versionMappingStrategy(true, null, versionMappingConf)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            componentResolver instanceof VersionMappingVariantDependencyResolver
            (componentResolver as VersionMappingVariantDependencyResolver).versionMappingConfiguration == versionMappingConf
            variantResolver.delegate == componentResolver
        }
    }

    def "fails if dependency mapping is enabled with no configuration"() {
        given:
        def variant = resolutionBacked(true, null)
        def versionMappingStrategy = versionMappingStrategy(true, null, null)

        when:
        factory.createCoordinateResolvers(variant, versionMappingStrategy)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Cannot enable dependency mapping without configuring a resolution configuration."
    }

    def "returns resolution backed resolver if dependency mapping is enabled with configuration"() {
        given:
        def conf = Mock(Configuration)
        def variant = resolutionBacked(true, conf)
        def versionMappingStrategy = Mock(VersionMappingStrategyInternal)

        expect:
        with(factory.createCoordinateResolvers(variant, versionMappingStrategy)) {
            variantResolver instanceof ResolutionBackedVariantDependencyResolver
            componentResolver instanceof ResolutionBackedComponentDependencyResolver
        }
    }

    SoftwareComponentVariant nonResolutionBacked() {
        Stub(SoftwareComponentVariant) {
            getAttributes() >> ImmutableAttributes.EMPTY
        }
    }

    ResolutionBackedVariant resolutionBacked(boolean enabled, @Nullable Configuration resolutionConfiguration) {
        Stub(ResolutionBackedVariant) {
            getAttributes() >> ImmutableAttributes.EMPTY
            getPublishResolvedCoordinates() >> enabled
            getResolutionConfiguration() >> resolutionConfiguration
        }
    }

    VersionMappingStrategyInternal versionMappingStrategy(boolean enabled, @Nullable Configuration defaultConf = null, @Nullable Configuration userConf = null) {
        Stub(VersionMappingStrategyInternal) {
            findStrategyForVariant(_ as ImmutableAttributes) >> Mock(VariantVersionMappingStrategyInternal) {
                isEnabled() >> enabled
                getDefaultResolutionConfiguration() >> defaultConf
                getUserResolutionConfiguration() >> userConf
            }
        }
    }
}
