/*
 * Copyright 2014 the original author or authors.
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

class CompositeResultsStoreTest extends Specification {
    def store1 = Mock(ResultsStore)
    def store2 = Mock(ResultsStore)
    def store = new CompositeResultsStore(store1, store2)
    def className = 'org.gradle.performance.MyPerformanceTest'
    def a = new PerformanceExperiment('testProject1', new PerformanceScenario(className, 'a'))
    def b = new PerformanceExperiment('testProject2', new PerformanceScenario(className, 'b'))
    def c = new PerformanceExperiment('testProject1', new PerformanceScenario(className, 'c'))
    def d = new PerformanceExperiment('testProject1', new PerformanceScenario(className, 'd'))

    def "returns union of test names"() {
        given:
        store1.performanceExperiments >> [a, b]
        store2.performanceExperiments >> [c, d]

        expect:
        store.performanceExperiments == [a, b, c, d]
    }

    def "delegates to appropriate store for details of given test"() {
        given:
        store1.performanceExperiments >> [a, b]
        store2.performanceExperiments >> [c, d]

        when:
        store.getTestResults(c, 'commits')

        then:
        1 * store2.getTestResults(c, 'commits')
    }
}
