/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.resolve.caching

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheDecorator
import org.gradle.cache.PersistentCache
import org.gradle.cache.IndexedCache
import org.gradle.cache.internal.DefaultInMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.VariantDerivationStrategy
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.snapshot.impl.StringValueSnapshot
import org.gradle.util.TestUtil
import org.gradle.util.internal.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.inject.Inject
import java.time.Duration

class ComponentMetadataRuleExecutorTest extends Specification {
    @Subject
    ComponentMetadataRuleExecutor executor
    GlobalScopedCacheBuilderFactory cacheBuilderFactory
    DefaultInMemoryCacheDecoratorFactory cacheDecoratorFactory
    ValueSnapshotter valueSnapshotter
    long time = 0
    BuildCommencedTimeProvider timeProvider = Stub(BuildCommencedTimeProvider) {
        getCurrentTime() >> { time }
    }
    IndexedCache<ValueSnapshot, CrossBuildCachingRuleExecutor.CachedEntry<ModuleComponentResolveMetadata>> store = Mock()
    Serializer<ModuleComponentResolveMetadata> serializer
    InstantiatingAction<ComponentMetadataContext> rule
    Transformer<ModuleComponentResolveMetadata, ComponentMetadataContext> detailsToResult
    Transformer<ComponentMetadataContext, ModuleVersionIdentifier> onCacheMiss
    CachePolicy cachePolicy

    ModuleComponentResolveMetadata result

    def setup() {
        def cacheBuilder
        cacheBuilder = Mock(CacheBuilder) {
            withLockOptions(_) >> { cacheBuilder }
            open() >> {
                Mock(PersistentCache) {
                    createIndexedCache(_) >> {
                        store
                    }
                }
            }
        }
        cacheBuilderFactory = Mock()
        cacheDecoratorFactory = Mock()
        cacheBuilderFactory.createCacheBuilder(_) >> cacheBuilder
        cacheDecoratorFactory.decorator(_, _) >> Mock(CacheDecorator)
        valueSnapshotter = Mock()
        serializer = Mock()
        cachePolicy = Mock()
        detailsToResult = Mock()
        onCacheMiss = Mock()
        executor = new ComponentMetadataRuleExecutor(cacheBuilderFactory, cacheDecoratorFactory, valueSnapshotter, timeProvider, serializer)
    }

    // Tests --refresh-dependencies behavior
    @Unroll("Cache expiry check refresh = #mustRefresh - #scenario - #ruleClass")
    def "expires entry when cache policy tells us to"() {
        def id = DefaultModuleVersionIdentifier.newId('org', 'foo', '1.0')
        def hashValue = TestHashCodes.hashCodeFrom(42)
        def key = Mock(ModuleComponentResolveMetadata)
        def inputsSnapshot = new StringValueSnapshot("1")
        def hasher = Hashing.newHasher()
        inputsSnapshot.appendToHasher(hasher)
        def keyHash = hasher.hash()
        def cachedResult = Mock(ModuleComponentResolveMetadata)
        Multimap<String, ImplicitInputRecord<?, ?>> implicits = HashMultimap.create()
        def record = Mock(ImplicitInputRecord)
        if (expired) {
            implicits.put('SomeService', record)
        }
        def cachedEntry = new CrossBuildCachingRuleExecutor.CachedEntry<ComponentMetadata>(0, implicits, cachedResult)
        def ruleServices = [:]
        def someService = Mock(SomeService)
        if (ruleClass == TestSupplierWithService) {
            ruleServices['SomeService'] = someService
        }
        def reexecute = mustRefresh || expired
        def moduleSources = new MutableModuleSources()
        moduleSources.add(new ModuleDescriptorHashModuleSource(hashValue, false))

        when:
        withRule(ruleClass, ruleServices)
        execute(key)

        then:
        1 * key.getSources() >> moduleSources
        1 * key.getVariantDerivationStrategy() >> Stub(VariantDerivationStrategy)
        1 * valueSnapshotter.snapshot(_) >> inputsSnapshot
        1 * store.getIfPresent(keyHash) >> cachedEntry
        if (expired) {
            // should check that the recorded service call returns the same value
            1 * record.getInput() >> '124'
            1 * record.getOutput() >> TestHashCodes.hashCodeFrom(10000)
            1 * cachedResult.isChanging() >> changing
            1 * cachedResult.getModuleVersionId() >> id
            1 * cachePolicy.moduleExpiry({ it.id == id }, Duration.ZERO, changing) >> Stub(Expiry) {
                isMustCheck() >> false
            }
            // we make it return false, this should invalidate the cache
            1 * someService.isUpToDate('124', TestHashCodes.hashCodeFrom(10000)) >> false
        } else {
            1 * cachedResult.isChanging() >> changing
            1 * cachedResult.getModuleVersionId() >> id
            1 * cachePolicy.moduleExpiry({ it.id == id }, Duration.ZERO, changing) >> Stub(Expiry) {
                isMustCheck() >> mustRefresh
            }
        }
        if (reexecute) {
            def details = Mock(ComponentMetadataContext)
            if (ruleClass == TestSupplierWithService) {
                // indicates that the service will be called
                1 * someService.withImplicitInputRecorder(_) >> someService
            }
            1 * onCacheMiss.transform(key) >> details
            1 * detailsToResult.transform(details) >> Mock(ModuleComponentResolveMetadata)
            1 * store.put(keyHash, _)
        }
        if (ruleClass == TestSupplierWithService && reexecute) {
            1 * someService.provide()
        }
        0 * _

        where:
        scenario                                           | changing | mustRefresh | expired | ruleClass
        'regular module caching'                           | false    | false       | false   | TestSupplier
        'regular module caching (--refresh-dependencies)'  | false    | true        | false   | TestSupplier
        'changing module caching'                          | true     | false       | false   | TestSupplier
        'changing module caching (--refresh-dependencies)' | true     | true        | false   | TestSupplier

        'regular module caching'                           | false    | false       | false   | TestSupplierWithService
        'regular module caching (--refresh-dependencies)'  | false    | true        | false   | TestSupplierWithService
        'changing module caching'                          | true     | false       | false   | TestSupplierWithService
        'changing module caching (--refresh-dependencies)' | true     | true        | false   | TestSupplierWithService

        'regular module caching'                           | false    | false       | true    | TestSupplierWithService
        'regular module caching (--refresh-dependencies)'  | false    | true        | true    | TestSupplierWithService
        'changing module caching'                          | true     | false       | true    | TestSupplierWithService
        'changing module caching (--refresh-dependencies)' | true     | true        | true    | TestSupplierWithService
    }

    void execute(ModuleComponentResolveMetadata key) {
        def executionResult = executor.execute(key, rule, detailsToResult, onCacheMiss, cachePolicy)
        result = executionResult
    }

    void withRule(Class<? extends Action<ComponentMetadataContext>> ruleClass, Map<String, Object> services = [:]) {
        def registry = new DefaultServiceRegistry()
        services.each { name, value ->
            registry.add(value.class, value)
        }
        def instantiator = new ImplicitInputsCapturingInstantiator(registry, TestUtil.instantiatorFactory()) {
            @Override
            def <IN, OUT, SERVICE> ImplicitInputsProvidingService<IN, OUT, SERVICE> findInputCapturingServiceByName(String name) {
                services[name]
            }
        }
        rule = new InstantiatingAction<>(
            DefaultConfigurableRules.of(DefaultConfigurableRule.of(ruleClass)),
            instantiator,
            shouldNotFail()
        )
    }


    InstantiatingAction.ExceptionHandler<ComponentMetadataContext> shouldNotFail() {
        return new InstantiatingAction.ExceptionHandler<ComponentMetadataContext>() {
            @Override
            void handleException(ComponentMetadataContext target, Throwable throwable) {
                throw new AssertionError("Expected the test not to fail, but it did", throwable)
            }
        }
    }

    @CacheableRule
    static class TestSupplier implements ComponentMetadataRule {

        @Override
        void execute(ComponentMetadataContext componentMetadataContext) {

        }
    }

    @CacheableRule
    static class TestSupplierWithService implements ComponentMetadataRule {

        final SomeService service

        @Inject
        TestSupplierWithService(SomeService service) {
            this.service = service
        }

        @Override
        void execute(ComponentMetadataContext componentMetadataContext) {
            service.provide()
        }
    }

    interface SomeService<SERVICE> extends ImplicitInputsProvidingService<String, HashCode, SERVICE> {
        void provide()
    }
}
