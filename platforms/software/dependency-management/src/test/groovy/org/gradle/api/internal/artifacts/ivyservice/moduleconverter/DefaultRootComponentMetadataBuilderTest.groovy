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
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependencyMetadataFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExternalModuleDependencyMetadataConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyMetadataConverter
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

class DefaultRootComponentMetadataBuilderTest extends AbstractProjectBuilderSpec {

    DependencyMetaDataProvider metaDataProvider = Mock(DependencyMetaDataProvider) {
        getModule() >> new AnonymousModule()
    }
    ImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    ExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter(new DefaultImmutableModuleIdentifierFactory())
    LocalVariantGraphResolveStateBuilder configurationStateBuilder = new DefaultLocalVariantGraphResolveStateBuilder(
        new ComponentIdGenerator(),
        new DefaultDependencyMetadataFactory(
            new ProjectDependencyMetadataConverter(excludeRuleConverter),
            new ExternalModuleDependencyMetadataConverter(excludeRuleConverter)
        ),
        excludeRuleConverter
    )

    def builderFactory = new DefaultRootComponentMetadataBuilder.Factory(
        moduleIdentifierFactory,
        new LocalComponentGraphResolveStateFactory(
            Stub(AttributeDesugaring),
            Stub(ComponentIdGenerator),
            configurationStateBuilder,
            TestUtil.calculatedValueContainerFactory(),
            TestUtil.inMemoryCacheFactory()
        ),
        AttributeTestUtil.services().getSchemaFactory(),
        configurationStateBuilder,
        TestUtil.calculatedValueContainerFactory()
    )

    RootComponentMetadataBuilder builder

    def setup() {
        ConfigurationsProvider configurationsProvider = project.configurations as ConfigurationsProvider
        builder = builderFactory.create(StandaloneDomainObjectContext.ANONYMOUS, configurationsProvider, metaDataProvider, AttributeTestUtil.mutableSchema())
    }

    def "caches root component resolve state and metadata"() {
        project.configurations.resolvable('conf')
        project.configurations.resolvable('conf-2')

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
        project.configurations.resolvable("root")
        project.configurations.consumable("conf")

        def root = builder.toRootComponent('root')
        def variant = root.rootComponent.candidatesForGraphVariantSelection.getVariantByConfigurationName('conf')

        when:
        builder.validator.validateMutation(mutationType)
        def otherRoot = builder.toRootComponent('root')

        then:
        root.rootComponent.is(otherRoot.rootComponent)
        root.rootComponent.metadata.is(otherRoot.rootComponent.metadata)
        !otherRoot.rootComponent.candidatesForGraphVariantSelection.getVariantByConfigurationName('conf').is(variant)

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
        project.configurations.resolvable("root")
        project.configurations.consumable("conf")

        def root = builder.toRootComponent('root')
        def variant = root.rootComponent.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf")

        when:
        builder.validator.validateMutation(mutationType)
        def otherRoot = builder.toRootComponent('root')

        then:
        root.rootComponent.is(otherRoot.rootComponent)
        root.rootComponent.metadata.is(otherRoot.rootComponent.metadata)
        otherRoot.rootComponent.candidatesForGraphVariantSelection.getVariantByConfigurationName('conf').is(variant)

        where:
        mutationType << [MutationValidator.MutationType.STRATEGY]
    }

}
