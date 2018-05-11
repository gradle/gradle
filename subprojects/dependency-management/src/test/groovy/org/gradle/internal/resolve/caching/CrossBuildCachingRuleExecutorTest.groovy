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
import org.gradle.internal.resolve.caching.CrossBuildCachingRuleExecutorTest.Details
import org.gradle.internal.resolve.caching.CrossBuildCachingRuleExecutorTest.Id
import org.gradle.internal.resolve.caching.CrossBuildCachingRuleExecutorTest.Result
import org.gradle.internal.serialize.Serializer
import org.gradle.util.BuildCommencedTimeProvider
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

class CrossBuildCachingRuleExecutorTest extends Specification {

    CacheRepository cacheRepository = Mock()
    InMemoryCacheDecoratorFactory cacheDecoratorFactory = Mock()
    ValueSnapshotter valueSnapshotter = Mock()
    BuildCommencedTimeProvider timeProvider = new BuildCommencedTimeProvider()
    PersistentIndexedCache<ValueSnapshot, CrossBuildCachingRuleExecutor.CachedEntry<Result>> store = Mock()
    CrossBuildCachingRuleExecutor.EntryValidator<Result> validator = Mock()
    Transformer<Serializable, Id> keyTransformer = new Transformer<Serializable, Id>() {
        @Override
        Serializable transform(Id id) {
            id.name
        }
    }
    Transformer<Result, Details> detailsToResult = new Transformer<Result, Details>() {
        @Override
        Result transform(Details details) {
            new Result(length: details.name.length())
        }
    }
    Transformer<Details, Id> onCacheMiss = new Transformer<Details, Id>() {
        @Override
        Details transform(Id id) {
            new Details(name: id.name)
        }
    }
    CachePolicy cachePolicy = Mock()
    InstantiatingAction<Details> rule

    Serializer<Result> resultSerializer = Mock()

    Result result

    @Subject
    CrossBuildCachingRuleExecutor<Id, Details, Result> executor

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
        cacheRepository.cache(_) >> cacheBuilder
        cacheDecoratorFactory.decorator(_, _) >> Mock(CacheDecorator)
        executor = new CrossBuildCachingRuleExecutor<Id, Details, Result>(
            "test",
            cacheRepository,
            cacheDecoratorFactory,
            valueSnapshotter,
            timeProvider,
            validator,
            keyTransformer,
            resultSerializer,
        )
    }

    def "doesn't do anything if rule is null"() {
        def id = new Id('Alicia')

        when:
        executor.execute(id, null, detailsToResult, onCacheMiss, cachePolicy)

        then:
        0 * _
    }

    def "executes the rule without caching if the rule is not cacheable"() {
        withNonCacheableToUpperCaseRule()
        def id = new Id('Alicia')

        when:
        execute(id)

        then:
        result.length == 6
        0 * _
    }

    def "executes the rule on cache miss"() {
        withToUpperCaseRule()
        def id = new Id('Alicia')

        when:
        execute(id)

        then:
        1 * valueSnapshotter.snapshot(_) >> {
            def snapshot = new StringValueSnapshot(it.toString())
            1 * store.put(snapshot, _)
            snapshot
        }
        1 * store.get(_) >> null
        0 * _
        result.length == 6
    }

    void "validates entries on cache hit"() {
        withToUpperCaseRule()
        def id = new Id('Alicia')

        when:
        execute(id)

        then:
        1 * valueSnapshotter.snapshot(_) >> {
            def snapshot = new StringValueSnapshot(it.toString())
            snapshot
        }
        1 * store.get(_) >> Mock(CrossBuildCachingRuleExecutor.CachedEntry) {
            getResult() >> new Result(length: 123)
        }
        1 * validator.isValid(_, _) >> true
        0 * _
        result.length == 123
    }

    void "if cache expired, re-executes the rule"() {
        withToUpperCaseRule()
        def snapshot
        def id = new Id('Alicia')

        when:
        execute(id)

        then:
        1 * valueSnapshotter.snapshot(_) >> {
            snapshot = new StringValueSnapshot(it.toString())
            snapshot
        }
        1 * store.get(_) >> Mock(CrossBuildCachingRuleExecutor.CachedEntry) {
            getResult() >> new Result(length: 123)
        }
        1 * validator.isValid(_, _) >> false
        1 * store.put({ it == snapshot }, { it.timestamp == timeProvider.currentTime })
        0 * _
        result.length == 6
    }


    void execute(Id id) {
        def executionResult = executor.execute(id, rule, detailsToResult, onCacheMiss, cachePolicy)
        result = executionResult
    }

    void withToUpperCaseRule() {
        rule = new InstantiatingAction<Details>(DefaultConfigurableRule.of(
            ToUpperCase
        ), TestUtil.instantiatorFactory().decorate(), shouldNotFail())
    }

    void withNonCacheableToUpperCaseRule() {
        rule = new InstantiatingAction<Details>(DefaultConfigurableRule.of(
            ToUpperCaseNotCached
        ), TestUtil.instantiatorFactory().decorate(), shouldNotFail())
    }

    InstantiatingAction.ExceptionHandler<Details> shouldNotFail() {
        return new InstantiatingAction.ExceptionHandler<Details>() {
            @Override
            void handleException(Details target, Throwable throwable) {
                throw new AssertionError("Expected the test not to fail, but it did", throwable)
            }
        }
    }

    static class Id {
        final String name

        Id(String name) {
            this.name = name
        }
    }

    static class Details {
        String name
    }

    static class Result {
        int length
    }

    @CacheableRule
    static class ToUpperCase implements Action<Details> {

        @Override
        void execute(Details details) {
            details.name = details.name.toUpperCase()
        }
    }

    static class ToUpperCaseNotCached implements Action<Details> {

        @Override
        void execute(Details details) {
            details.name = details.name.toUpperCase()
        }
    }
}
