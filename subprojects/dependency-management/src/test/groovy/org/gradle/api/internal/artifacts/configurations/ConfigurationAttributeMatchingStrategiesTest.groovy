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

class ConfigurationAttributeMatchingStrategiesTest extends Specification {
    @Unroll("Finds best matches: #scenario")
    def "finds best match"() {
        given:
        def strategy = new DefaultConfigurationAttributesMatchingStrategy()

        when:
        def bestMatches = ConfigurationAttributeMatchingStrategies.findBestMatches(strategy, source, candidates)

        then:
        bestMatches == expected

        where:
        scenario                           | source               | candidates                                                  | expected
        'one exact match'                  | [a: 'foo']           | [first: [a: 'foo'], second: [a: 'bar']]                     | ['first']
        'two exact matches'                | [a: 'foo']           | [first: [a: 'foo'], second: [a: 'foo']]                     | ['first', 'second']
        'no matches'                       | [a: 'foo']           | [first: [a: 'bar'], second: [a: 'bar']]                     | []
        'one exact match (2 attributes)'   | [a: 'foo', b: 'bar'] | [first: [a: 'foo', b: 'foo'], second: [a: 'foo', b: 'bar']] | ['second']
        'two exact matches (2 attributes)' | [a: 'foo', b: 'bar'] | [first: [a: 'foo', b: 'bar'], second: [a: 'foo', b: 'bar']] | ['first', 'second']
        'no matches (2 attributes)'        | [a: 'foo', b: 'bar'] | [first: [a: 'foo', b: 'foo'], second: [a: 'foo', b: 'foo']] | []
    }

    @Unroll("Finds best matches with default value: #scenario")
    def "finds best match with default value"() {
        given:
        def strategy = new DefaultConfigurationAttributesMatchingStrategy()
        strategy.matcher('b') {
            it.matchAlways()
        }
        when:
        def bestMatches = ConfigurationAttributeMatchingStrategies.findBestMatches(strategy, source, candidates)

        then:
        bestMatches == expected

        where:
        scenario                               | source               | candidates                                                  | expected
        'one exact match'                      | [a: 'foo']           | [first: [a: 'foo'], second: [a: 'bar']]                     | ['first']
        'two exact matches'                    | [a: 'foo']           | [first: [a: 'foo'], second: [a: 'foo']]                     | ['first', 'second']
        'no matches'                           | [a: 'foo']           | [first: [a: 'bar'], second: [a: 'bar']]                     | []
        'one exact match (2 attributes)'       | [a: 'foo', b: 'bar'] | [first: [a: 'foo', b: 'foo'], second: [a: 'foo', b: 'bar']] | ['second']
        'two exact matches (2 attributes)'     | [a: 'foo', b: 'bar'] | [first: [a: 'foo', b: 'bar'], second: [a: 'foo', b: 'bar']] | ['first', 'second']
        'no matches (2 attributes)'            | [a: 'foo', b: 'bar'] | [first: [a: 'foo', b: 'foo'], second: [a: 'foo', b: 'foo']] | []
        'partial match (2 attributes)'         | [a: 'foo', b: 'bar'] | [first: [a: 'foo'], second: [a: 'foo']]                     | ['first', 'second']
        'exact match preferred (2 attributes)' | [a: 'foo', b: 'bar'] | [first: [a: 'foo'], second: [a: 'foo', b: 'bar']]           | ['second']
        'no default value (2 attributes)'      | [a: 'foo', b: 'bar'] | [first: [b: 'bar'], second: [b: 'bar']]                     | []
    }

    @Unroll("Finds best matches with custom scorer: #scenario")
    def "finds best match with custom scorer"() {
        given:
        def strategy = new DefaultConfigurationAttributesMatchingStrategy()
        strategy.matcher('a') {
            it.scorer = { String a, String b ->
                Integer.valueOf(a - 'java') - Integer.valueOf(b - 'java')
            }
        }
        when:
        def bestMatches = ConfigurationAttributeMatchingStrategies.findBestMatches(strategy, source, candidates)

        then:
        bestMatches == expected

        where:
        scenario                               | source                 | candidates                                                                                     | expected
        'one exact match'                      | [a: 'java5']           | [first: [a: 'java5'], second: [a: 'java6']]                                                    | ['first']
        'two exact matches'                    | [a: 'java5']           | [first: [a: 'java5'], second: [a: 'java5']]                                                    | ['first', 'second']
        'no matches'                           | [a: 'java5']           | [first: [a: 'java6'], second: [a: 'java6']]                                                    | []
        'one proximity match'                  | [a: 'java6']           | [first: [a: 'java7'], second: [a: 'java5']]                                                    | ['second']
        'closest match'                        | [a: 'java6']           | [first: [a: 'java3'], second: [a: 'java5'], third: [a: 'java4']]                               | ['second']
        '2 closest matches'                    | [a: 'java6']           | [first: [a: 'java5'], second: [a: 'java5'], third: [a: 'java4']]                               | ['first', 'second']
        '2 closest matches with discriminator' | [a: 'java6', b: 'foo'] | [first: [a: 'java5', b: 'foo'], second: [a: 'java5', b: 'bar'], third: [a: 'java4', b: 'foo']] | ['first']
    }
}
