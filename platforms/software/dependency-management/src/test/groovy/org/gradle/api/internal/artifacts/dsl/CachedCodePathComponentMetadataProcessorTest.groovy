/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.api.specs.Specs
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheDecorator
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.DefaultInMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.MutableComponentVariant
import org.gradle.internal.component.external.model.NoOpDerivationStrategy
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.snapshot.impl.StringValueSnapshot
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.gradle.util.internal.SimpleMapInterner
import org.junit.Assert
import spock.lang.Issue
import spock.lang.Specification

import static org.hamcrest.CoreMatchers.sameInstance
import static org.junit.Assume.assumeThat

class CachedCodePathComponentMetadataProcessorTest extends Specification {
    def instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def stringInterner = SimpleMapInterner.notThreadSafe()
    def mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()
    def ivyMetadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    def dependencyMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DirectDependencyMetadataImpl, stringInterner)
    def dependencyConstraintMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DependencyConstraintMetadataImpl, stringInterner)
    def componentIdentifierNotationParser = new ComponentIdentifierParserFactory().create()
    def metadataRuleContainer = new ComponentMetadataRuleContainer()
    MetadataResolutionContext context = Mock(MetadataResolutionContext) {
        injectingInstantiator >> instantiator
    }

    @Issue("https://github.com/gradle/gradle/issues/20145")
    def "keeps additional variants produced by rules in #metadataKind metadata with #ownVariants own variants"() {
        given: "a component metadata with no derivation strategy"
        def metadata = metadata(metadataKind)
        assumeThat(metadata.getVariantDerivationStrategy(), sameInstance(NoOpDerivationStrategy.instance))

        and: "some variants in the component, or none"
        (0..<ownVariants).forEach {
            metadata.addVariant("variant$it", ImmutableAttributes.EMPTY)
        }

        and: "a rule that adds a variant to all components"
        metadataRuleContainer.addClassRule(
            new SpecConfigurableRule(
                DefaultConfigurableRule.of(TestAddVariantComponentMetadataRule),
                Specs.satisfyAll()
            )
        )

        when: "a processor runs on this metadata through the caching code path"
        def processor = processorWithCacheThatNeverHits()
        def result = processor.processMetadata(metadata.asImmutable())

        then: "the result should have the variant added by the rule"
        def variantsForGraphTraversal = result.variantsForGraphTraversal
        variantsForGraphTraversal.size() == ownVariants + 1
        def variant = variantsForGraphTraversal.find { it.name == "test" }
        variant != null
        "org.gradle.test" in variant.attributes.keySet().collect { it.name }

        where:
        metadataKind | ownVariants
        "maven"      | 0
        "maven"      | 2
        "ivy"        | 0
        "ivy"        | 2
    }

    private DefaultComponentMetadataProcessor processorWithCacheThatNeverHits() {
        CacheBuilder cacheBuilder
        cacheBuilder = Mock(CacheBuilder) {
            withInitialLockMode(_ as FileLockManager.LockMode) >> { cacheBuilder }
            open() >> {
                Mock(PersistentCache) {
                    createIndexedCache(_ as IndexedCacheParameters) >> {
                        Mock(IndexedCache) {
                            getIfPresent(_) >> null
                        }
                    }
                }
            }
        }
        GlobalScopedCacheBuilderFactory cacheRepository = Mock(GlobalScopedCacheBuilderFactory) {
            createCacheBuilder(_ as String) >> cacheBuilder
        }

        DefaultInMemoryCacheDecoratorFactory cacheDecoratorFactory = Mock(DefaultInMemoryCacheDecoratorFactory) {
            decorator(_ as int, _ as boolean) >> Mock(CacheDecorator)
        }
        BuildCommencedTimeProvider timeProvider = Stub(BuildCommencedTimeProvider) {
            getCurrentTime() >> { 0L }
        }

        def snapshotter = Mock(ValueSnapshotter) {
            snapshot(_) >> {
                new StringValueSnapshot(it.toString())
            }
        }
        def executorWithCache = new ComponentMetadataRuleExecutor(cacheRepository, cacheDecoratorFactory, snapshotter, timeProvider, Mock(Serializer))

        new DefaultComponentMetadataProcessor(
            metadataRuleContainer, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser,
            AttributeTestUtil.attributesFactory(), executorWithCache, DependencyManagementTestUtil.platformSupport(), context
        )
    }

    private AbstractMutableModuleComponentResolveMetadata metadata(String metadataKind, MutableComponentVariant... variants) {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def metadata =
            metadataKind == "maven" ?
                mavenMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"), []) :
                metadataKind == "ivy" ?
                    ivyMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"), [])
                    : Assert.fail("unexpected metadataKind")
        for (final def variant in variants) {
            metadata.addVariant(variant)
        }
        metadata.status = "integration"
        metadata.statusScheme = ["integration", "release"]
        metadata.sources = MutableModuleSources.of(Stub(ModuleDescriptorHashModuleSource) {
            getDescriptorHash() >> HashCode.fromBytes([0, 0, 0, 0] as byte[])
        })
        return metadata as AbstractMutableModuleComponentResolveMetadata
    }
}
