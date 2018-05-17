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

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.changedetection.state.StringValueSnapshot
import org.gradle.api.internal.changedetection.state.ValueSnapshot
import org.gradle.api.internal.changedetection.state.ValueSnapshotter
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheDecorator
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.PersistentIndexedCache
import org.gradle.internal.reflect.DefaultConfigurableRule
import org.gradle.internal.reflect.InstantiatingAction
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.util.BuildCommencedTimeProvider
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor.MAX_AGE

import javax.inject.Inject

class ComponentMetadataSupplierRuleExecutorTest extends Specification {
    @Subject
    ComponentMetadataSupplierRuleExecutor executor
    CacheRepository cacheRepository
    InMemoryCacheDecoratorFactory cacheDecoratorFactory
    ValueSnapshotter valueSnapshotter
    long time = 0
    BuildCommencedTimeProvider timeProvider = Stub(BuildCommencedTimeProvider) {
        getCurrentTime() >> { time }
    }
    PersistentIndexedCache<ValueSnapshot, CrossBuildCachingRuleExecutor.CachedEntry<ComponentMetadata>> store = Mock()
    Serializer<ComponentMetadata> serializer
    InstantiatingAction<ComponentMetadataSupplierDetails> rule
    Transformer<ComponentMetadata, ComponentMetadataSupplierDetails> detailsToResult
    Transformer<ComponentMetadataSupplierDetails, ModuleVersionIdentifier> onCacheMiss
    CachePolicy cachePolicy

    ComponentMetadata result

    def setup() {
        def cacheBuilder
        cacheBuilder = Mock(CacheBuilder) {
            withLockOptions(_) >> { cacheBuilder }
            open() >> {
                Mock(PersistentCache) {
                    createCache(_) >> {
                        store
                    }
                }
            }
        }
        cacheRepository = Mock()
        cacheDecoratorFactory = Mock()
        cacheRepository.cache(_) >> cacheBuilder
        cacheDecoratorFactory.decorator(_, _) >> Mock(CacheDecorator)
        valueSnapshotter = Mock()
        serializer = Mock()
        cachePolicy = Mock()
        detailsToResult = Mock()
        onCacheMiss = Mock()
        executor = new ComponentMetadataSupplierRuleExecutor(cacheRepository, cacheDecoratorFactory, valueSnapshotter, timeProvider, serializer)
    }

    // Tests --refresh-dependencies behavior
    @Unroll("Cache expiry check age=#age, refresh = #mustRefresh - #scenario - #ruleClass")
    def "expires entry when cache policy tells us to"() {
        def id = DefaultModuleVersionIdentifier.newId('org', 'foo', '1.0')
        def inputsSnapshot = new StringValueSnapshot("1")
        def cachedResult = Mock(ComponentMetadata)
        def cachedEntry = new CrossBuildCachingRuleExecutor.CachedEntry<ComponentMetadata>(0, cachedResult)
        def ruleServices = []
        def someService = Mock(SomeService)
        if (ruleClass == TestSupplierWithService) {
            ruleServices << someService
        }
        time = age
        boolean expired = age > MAX_AGE
        def reexecute = mustRefresh || expired

        when:
        withRule(ruleClass, ruleServices)
        execute(id)

        then:
        1 * valueSnapshotter.snapshot(_) >> inputsSnapshot
        1 * store.get(inputsSnapshot) >> cachedEntry
        if (!expired) {
            1 * cachedResult.isChanging() >> changing
            1 * cachedResult.getId() >> id
            1 * cachePolicy.mustRefreshModule({ it.id == id }, age, changing) >> mustRefresh
        }
        if (reexecute) {
            def details = Mock(ComponentMetadataSupplierDetails)
            1 * onCacheMiss.transform(id) >> details
            1 * detailsToResult.transform(details) >> Mock(ComponentMetadata)
            1 * store.put(inputsSnapshot, _)
        }
        // This shows a limitation of the current implementation:
        // if a service is used and that this service has an expiry, then we
        // would only call the rule again if must refresh == true, that is
        // to say when --refresh-dependencies is called. In other words if
        // the service provides an implicit input, a change in that input is
        // not discovered. To workaround this we have a hard limit of 24h set
        if (ruleClass == TestSupplierWithService && reexecute) {
            1 * someService.provide()
        }
        0 * _

        where:
        scenario                                           | changing | mustRefresh | age         | ruleClass
        'regular module caching'                           | false    | false       | 0           | TestSupplier
        'regular module caching (--refresh-dependencies)'  | false    | true        | 0           | TestSupplier
        'changing module caching'                          | true     | false       | 0           | TestSupplier
        'changing module caching (--refresh-dependencies)' | true     | true        | 0           | TestSupplier

        'regular module caching'                           | false    | false       | 0           | TestSupplierWithService
        'regular module caching (--refresh-dependencies)'  | false    | true        | 0           | TestSupplierWithService
        'changing module caching'                          | true     | false       | 0           | TestSupplierWithService
        'changing module caching (--refresh-dependencies)' | true     | true        | 0           | TestSupplierWithService

        'regular module caching'                           | false    | false       | MAX_AGE + 1 | TestSupplier
        'regular module caching (--refresh-dependencies)'  | false    | true        | MAX_AGE + 1 | TestSupplier
        'changing module caching'                          | true     | false       | MAX_AGE + 1 | TestSupplier
        'changing module caching (--refresh-dependencies)' | true     | true        | MAX_AGE + 1 | TestSupplier

        'regular module caching'                           | false    | false       | MAX_AGE + 1 | TestSupplierWithService
        'regular module caching (--refresh-dependencies)'  | false    | true        | MAX_AGE + 1 | TestSupplierWithService
        'changing module caching'                          | true     | false       | MAX_AGE + 1 | TestSupplierWithService
        'changing module caching (--refresh-dependencies)' | true     | true        | MAX_AGE + 1 | TestSupplierWithService
    }

    void execute(ModuleVersionIdentifier id) {
        def executionResult = executor.execute(id, rule, detailsToResult, onCacheMiss, cachePolicy)
        result = executionResult
    }

    void withRule(Class<? extends Action<ComponentMetadataSupplierDetails>> ruleClass, List<Object> services = []) {
        def instantiator
        if (services) {
            def registry = new DefaultServiceRegistry()
            services.each {
                registry.add(it.class, it)
            }
            instantiator = TestUtil.instantiatorFactory().inject(registry)
        } else {
            instantiator = TestUtil.instantiatorFactory().decorate()
        }
        rule = new InstantiatingAction<>(
            DefaultConfigurableRule.of(ruleClass),
            instantiator,
            shouldNotFail()
        )
    }

    InstantiatingAction.ExceptionHandler<ComponentMetadataSupplierDetails> shouldNotFail() {
        return new InstantiatingAction.ExceptionHandler<ComponentMetadataSupplierDetails>() {
            @Override
            void handleException(ComponentMetadataSupplierDetails target, Throwable throwable) {
                throw new AssertionError("Expected the test not to fail, but it did", throwable)
            }
        }
    }

    @CacheableRule
    static class TestSupplier implements Action<ComponentMetadataSupplierDetails> {

        @Override
        void execute(ComponentMetadataSupplierDetails componentMetadataSupplierDetails) {

        }
    }

    @CacheableRule
    static class TestSupplierWithService implements Action<ComponentMetadataSupplierDetails> {

        final SomeService service

        @Inject
        TestSupplierWithService(SomeService service) {
            this.service = service
        }

        @Override
        void execute(ComponentMetadataSupplierDetails componentMetadataSupplierDetails) {
            service.provide()
        }
    }

    interface SomeService {
        void provide()
    }
}
