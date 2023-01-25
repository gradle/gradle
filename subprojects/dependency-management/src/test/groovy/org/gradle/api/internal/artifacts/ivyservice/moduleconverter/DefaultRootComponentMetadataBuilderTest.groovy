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

import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultRootComponentMetadataBuilderTest extends Specification {

    DependencyMetaDataProvider metaDataProvider = Mock() {
        getModule() >> Mock(Module)
    }
    ComponentIdentifierFactory componentIdentifierFactory = Mock()
    ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()
    LocalConfigurationMetadataBuilder configurationMetadataBuilder = Mock()
    ConfigurationInternal configuration = Mock()
    def configurationsProvider = Stub(DefaultConfigurationContainer) {
        getAll() >> ([configuration] as Set)
        findByName("conf") >> configuration
        size() >> 1
    }
    ProjectStateRegistry projectStateRegistry = Mock()
    DependencyLockingProvider dependencyLockingProvider = Mock()

    DefaultLocalComponentMetadata.DefaultLocalConfigurationMetadata configurationMetadata = Mock()

    def mid = DefaultModuleIdentifier.newId('foo', 'bar')

    def builderFactory = new DefaultRootComponentMetadataBuilder.Factory(
        metaDataProvider,
        componentIdentifierFactory,
        moduleIdentifierFactory,
        configurationMetadataBuilder,
        projectStateRegistry,
        dependencyLockingProvider,
        TestUtil.calculatedValueContainerFactory()
    )

    def builder = builderFactory.create(configurationsProvider)

    def setup() {
        ConfigurationPublications outgoing = Mock()
        outgoing.getCapabilities() >> Collections.emptySet()

        ResolutionStrategyInternal resolutionStrategy = Mock()
        resolutionStrategy.isDependencyLockingEnabled() >> false

        configuration.getHierarchy() >> ([configuration] as Set)
        configuration.getExtendsFrom() >> Collections.emptySet()
        configuration.getOutgoing() >> outgoing
        configuration.getResolutionStrategy() >> resolutionStrategy
        configuration.getName() >> "conf"
        configuration.getDescription() >> "desc"
        configuration.getAttributes() >> ImmutableAttributes.EMPTY
    }

    def "caches root component metadata"() {
        componentIdentifierFactory.createComponentIdentifier(_) >> {
            new DefaultModuleComponentIdentifier(mid, '1.0')
        }
        def root = builder.toRootComponentMetaData()

        when:
        def otherRoot = builder.toRootComponentMetaData()

        then:
        otherRoot.is(root)
    }

    def "doesn't cache root component metadata when module identifier changes"() {
        1 * componentIdentifierFactory.createComponentIdentifier(_) >> {
            new DefaultModuleComponentIdentifier(mid, '1.0')
        }
        def root = builder.toRootComponentMetaData()

        when:
        componentIdentifierFactory.createComponentIdentifier(_) >> {
            new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('foo', 'baz'), '1.0')
        }

        def otherRoot = builder.toRootComponentMetaData()

        then:
        !otherRoot.is(root)
    }

    def "reevaluates component metadata when #mutationType change"() {
        componentIdentifierFactory.createComponentIdentifier(_) >> {
            new DefaultModuleComponentIdentifier(mid, '1.0')
        }
        def root = builder.toRootComponentMetaData()

        def conf = root.getConfiguration("conf")
        assert conf.needsEvaluation()
        conf.realizeDependencies()
        assert !conf.needsEvaluation()

        when:
        builder.validator.validateMutation(mutationType)
        def otherRoot = builder.toRootComponentMetaData()

        then:
        root == otherRoot
        root.getConfiguration("conf").needsEvaluation()

        where:
        mutationType << [
            MutationValidator.MutationType.DEPENDENCIES,
            MutationValidator.MutationType.ARTIFACTS,
        ]
    }

    def "does not reevaluate component metadata when #mutationType change"() {
        componentIdentifierFactory.createComponentIdentifier(_) >> {
            new DefaultModuleComponentIdentifier(mid, '1.0')
        }
        def root = builder.toRootComponentMetaData()

        def conf = root.getConfiguration("conf")
        assert conf.needsEvaluation()
        conf.realizeDependencies()
        assert !conf.needsEvaluation()

        when:
        builder.validator.validateMutation(mutationType)
        def otherRoot = builder.toRootComponentMetaData()

        then:
        root == otherRoot
        !conf.needsEvaluation()

        where:
        mutationType << [
                MutationValidator.MutationType.ROLE,
                MutationValidator.MutationType.STRATEGY,
        ]
    }
}
