/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter

import org.gradle.api.internal.artifacts.AnonymousModule
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantMetadataBuilder
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultRootComponentMetadataBuilderTest extends Specification {

    DependencyMetaDataProvider metaDataProvider = Mock(DependencyMetaDataProvider) {
        getModule() >> new AnonymousModule()
    }
    ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()
    LocalVariantMetadataBuilder configurationMetadataBuilder = Mock(LocalVariantMetadataBuilder) {
        create(_, _, _, _, _, _) >> { args ->
            Mock(LocalVariantGraphResolveMetadata)
        }
    }

    def configurationsProvider = Stub(ConfigurationsProvider)

    def builderFactory = new DefaultRootComponentMetadataBuilder.Factory(
        moduleIdentifierFactory,
        new LocalComponentGraphResolveStateFactory(
            Stub(AttributeDesugaring),
            Stub(ComponentIdGenerator),
            configurationMetadataBuilder,
            TestUtil.calculatedValueContainerFactory()
        )
    )

    def builder = builderFactory.create(StandaloneDomainObjectContext.ANONYMOUS, configurationsProvider, metaDataProvider, EmptySchema.INSTANCE)

    def "caches root component resolve state and metadata"() {
        configurationsProvider.findByName('conf') >> resolvable()
        configurationsProvider.findByName('conf-2') >> resolvable()

        def root = builder.toRootComponent('conf')

        when:
        def sameConf = builder.toRootComponent('conf')

        then:
        sameConf.rootComponent.is(root.rootComponent)
        sameConf.rootComponent.metadata.is(root.rootComponent.metadata)

        when:
        def differentConf = builder.toRootComponent('conf-2')

        then:
        differentConf.rootComponent.is(root.rootComponent)
        differentConf.rootComponent.metadata.is(root.rootComponent.metadata)
    }

    def "reevaluates component metadata when #mutationType change"() {
        configurationsProvider.findByName('root') >> resolvable()
        configurationsProvider.findByName('conf') >> resolvable()

        def root = builder.toRootComponent('root')
        def variant = root.rootComponent.getConfigurationLegacy('conf')

        when:
        builder.validator.validateMutation(mutationType)
        def otherRoot = builder.toRootComponent('root')

        then:
        root.rootComponent.is(otherRoot.rootComponent)
        root.rootComponent.metadata.is(otherRoot.rootComponent.metadata)
        !otherRoot.rootComponent.getConfigurationLegacy('conf').is(variant)

        when:

        where:
        mutationType << [
            MutationValidator.MutationType.DEPENDENCIES,
            MutationValidator.MutationType.DEPENDENCY_ATTRIBUTES,
            MutationValidator.MutationType.DEPENDENCY_CONSTRAINT_ATTRIBUTES,
            MutationValidator.MutationType.ARTIFACTS,
            MutationValidator.MutationType.USAGE,
            MutationValidator.MutationType.HIERARCHY
        ]
    }

    def "does not reevaluate component metadata when #mutationType change"() {
        configurationsProvider.findByName('root') >> resolvable()
        configurationsProvider.findByName('conf') >> resolvable()

        def root = builder.toRootComponent('root')
        def variant = root.rootComponent.getConfigurationLegacy("conf")

        when:
        builder.validator.validateMutation(mutationType)
        def otherRoot = builder.toRootComponent('root')

        then:
        root.rootComponent.is(otherRoot.rootComponent)
        root.rootComponent.metadata.is(otherRoot.rootComponent.metadata)
        otherRoot.rootComponent.getConfigurationLegacy('conf').is(variant)

        where:
        mutationType << [MutationValidator.MutationType.STRATEGY]
    }

    private ConfigurationInternal resolvable() {
        Mock(ConfigurationInternal) {
            isCanBeResolved() >> true
            getAttributes() >> ImmutableAttributes.EMPTY
        }
    }
}
