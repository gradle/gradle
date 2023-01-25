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

package org.gradle.internal.component.local.model

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata.DefaultLocalConfigurationMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.deprecation.DeprecationMessageBuilder
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ModelContainer
import spock.lang.Specification

import java.util.function.Function
import java.util.function.Supplier

class AbstractLocalComponentMetadataTest extends Specification {
    def configurationMetadataBuilder = Mock(LocalConfigurationMetadataBuilder)

    def moduleVersionId = DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId("org", "name"), "rev")
    def componentId = DefaultModuleComponentIdentifier.newId(moduleVersionId)

    def "adds artifacts from each configuration"() {
        def sourceConfig1 = Stub(ConfigurationInternal)
        def sourceConfig2 = Stub(ConfigurationInternal)
        def targetConfig1 = Mock(DefaultLocalConfigurationMetadata)
        def targetConfig2 = Mock(DefaultLocalConfigurationMetadata)
        def artifacts1 = [Stub(PublishArtifact)] as Set
        def artifacts2 = [Stub(PublishArtifactSet)] as Set
        def configurator1 = Mock(org.gradle.api.Action)
        def configurator2 = Mock(org.gradle.api.Action)

        given:
        sourceConfig1.name >> "config1"
        sourceConfig1.getHierarchy() >> ([sourceConfig1] as Set)
        sourceConfig1.collectVariants(_) >> { ConfigurationInternal.VariantVisitor visitor ->
            visitor.visitArtifacts(artifacts1)
            visitor.visitChildVariant("child1", null, null, null, null)
            visitor.visitChildVariant("child2", null, null, null, null)
        }
        sourceConfig2.name >> "config2"
        sourceConfig2.getHierarchy() >> ([sourceConfig2] as Set)
        sourceConfig2.collectVariants(_) >> { ConfigurationInternal.VariantVisitor visitor ->
            visitor.visitArtifacts(artifacts2)
        }

        def converter = newConverter(name -> {
            if (name == sourceConfig1.name) {
                return targetConfig1
            } else if (name == sourceConfig2.name) {
                return targetConfig2
            }
        })

        when:
        converter.registerConfiguration(configurationMetadataBuilder, sourceConfig1, configurator1)
        converter.registerConfiguration(configurationMetadataBuilder, sourceConfig2, configurator2)
        converter.getConfiguration(sourceConfig1.name)
        converter.getConfiguration(sourceConfig2.name)

        // TODO: Test more thoroughly.
        then:
        1 * targetConfig1.addArtifacts(artifacts1)
        1 * targetConfig1.addVariant("config1-child1", _, _, _, _, _)
        1 * targetConfig1.addVariant("config1-child2", _, _, _, _, _)
        1 * targetConfig2.addArtifacts(artifacts2)
    }

    private AbstractLocalComponentMetadata<?> newConverter(Function<String, DefaultLocalConfigurationMetadata> configurationFactory) {
        return new AbstractLocalComponentMetadata<DefaultLocalConfigurationMetadata>(moduleVersionId, componentId, null, null, null, null) {
            @Override
            DefaultLocalConfigurationMetadata createConfiguration(String name, String description, boolean visible, boolean transitive, Set<String> extendsFrom, ImmutableSet<String> hierarchy, ImmutableAttributes attributes, boolean canBeConsumed, DeprecationMessageBuilder.WithDocumentation consumptionDeprecation, boolean canBeResolved, ImmutableCapabilities capabilities, ModelContainer<?> model, CalculatedValueContainerFactory calculatedValueContainerFactory, Supplier<List<DependencyConstraint>> consistentResolutionConstraints) {
                return configurationFactory.apply(name)
            }

            @Override
            List<? extends DependencyMetadata> getSyntheticDependencies(String configuration) {
                return Collections.emptyList()
            }
        }
    }
}
