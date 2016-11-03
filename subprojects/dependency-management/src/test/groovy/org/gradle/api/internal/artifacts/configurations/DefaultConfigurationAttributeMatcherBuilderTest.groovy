/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations

import spock.lang.Specification
import spock.lang.Unroll

class DefaultConfigurationAttributeMatcherBuilderTest extends Specification {
    def builder = DefaultConfigurationAttributeMatcherBuilder.newBuilder()

    @Unroll
    def "defaults to strict case sensitive match"() {
        when:
        def matcher = builder.build()

        then:
        matcher.score(requested, provided) == score

        where:
        requested | provided | score
        'foo'     | 'foo'    | 0
        'foo'     | null     | -1
        'foo'     | 'FOO'    | -1
    }

    @Unroll
    def "can have case insensitive match"() {
        when:
        def matcher = builder
            .setScorer(DefaultConfigurationAttributeMatcherBuilder.STRICT_CASE_INSENSITIVE)
            .build()

        then:
        matcher.score(requested, provided) == score

        where:
        requested | provided | score
        'foo'     | 'foo'    | 0
        'foo'     | null     | -1
        'foo'     | 'FOO'    | 0
        'Foo'     | 'fOO'    | 0
    }

    @Unroll
    def "can have case insensitive match with DSL shortcut"() {
        when:
        def matcher = builder
            .ignoreCase()
            .build()

        then:
        matcher.score(requested, provided) == score

        where:
        requested | provided | score
        'foo'     | 'foo'    | 0
        'foo'     | null     | -1
        'foo'     | 'FOO'    | 0
        'Foo'     | 'fOO'    | 0
    }

    def "can match always"() {
        when:
        def matcher = builder
            .setDefaultValue(DefaultConfigurationAttributeMatcherBuilder.AUTO_DEFAULT)
            .build()

        then:
        matcher.score('foo', null) == -1
        matcher.defaultValue('foo') == 'foo'
    }

    def "can match always with DSL shortcut"() {
        when:
        def matcher = builder
            .matchAlways()
            .build()

        then:
        matcher.score('foo', null) == -1
        matcher.defaultValue('foo') == 'foo'
    }

    def "can return constant"() {
        when:
        def matcher = builder
            .setDefaultValue('blah')
            .build()

        then:
        matcher.score('foo', null) == -1
        matcher.defaultValue('foo') == 'blah'
    }


    @Unroll
    def "can have provide default value"() {
        when:
        def matcher = builder
            .setDefaultValue(provider)
            .build()

        then:
        matcher.defaultValue(requested) == defaultValue

        where:
        requested | provider                                                 | defaultValue
        'foo'     | DefaultConfigurationAttributeMatcherBuilder.AUTO_DEFAULT | 'foo'
        'foo'     | DefaultConfigurationAttributeMatcherBuilder.NO_DEFAULT   | null
    }
}
