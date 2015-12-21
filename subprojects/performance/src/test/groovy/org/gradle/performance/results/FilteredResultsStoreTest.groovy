/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance.results

import spock.lang.Specification
import spock.lang.Unroll

import static java.util.regex.Pattern.compile

class FilteredResultsStoreTest extends Specification {

    @Unroll
    def "Uses the correct filter"() {
        AllResultsStore allResultsStore = Mock() {
            getTestNames() >> ['alpha', 'beta', 'gamma', 'delta']
        }

        when:
        def filter = new FilteredResultsStore(allResultsStore, pattern, include)

        then:
        filter.getTestNames() == expected

        where:
        pattern          | include | expected
        compile('alpha') | true    | ['alpha']
        compile('alpha') | false   | ['beta', 'gamma', 'delta']
        compile('zeta')  | true    | []
        compile('zeta')  | false   | ['alpha', 'beta', 'gamma', 'delta']
    }
}
