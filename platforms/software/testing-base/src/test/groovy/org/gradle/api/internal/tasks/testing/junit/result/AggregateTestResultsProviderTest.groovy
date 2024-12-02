/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.BuildableTestResultsProvider
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification

class AggregateTestResultsProviderTest extends Specification {
    def "visits results from each provider"() {
        given:
        def root1 = new BuildableTestResultsProvider().tap {
            result("root-1")
            child {
                result("class-1")
            }
        }
        def root2 = new BuildableTestResultsProvider().tap {
            result("root-2")
            child {
                result("class-2")
            }
        }
        def aggregate = new AggregateTestResultsProvider("Aggregate", "Aggregate", [root1, root2])

        when:
        List<PersistentTestResult> results = []
        aggregate.visitChildren {
            results.add(it.result)
        }

        then:
        results*.name == ['class-1', 'class-2']
    }

    def "processes duplicate classes"() {
        def root1 = new BuildableTestResultsProvider().tap {
            result("root-1")
            child {
                result("class-1")
                child {
                    result("test-a")
                }
            }
        }
        def root2 = new BuildableTestResultsProvider().tap {
            result("root-2")
            child {
                result("class-1")
                child {
                    result("test-b")
                }
            }
        }
        def aggregate = new AggregateTestResultsProvider("Aggregate", "Aggregate", [root1, root2])

        when:
        List<PersistentTestResult> results = []
        aggregate.visitChildren {
            results.add(it.result)
            it.visitChildren {
                results.add(it.result)
            }
        }

        then:
        results*.name == ['class-1', 'test-a', 'test-b']
    }

    def "merge methods in duplicate classes"() {
        final long startTimeSooner = 122000
        final long startTimeLater = 123000
        final long endTimeSooner = 123456
        final long endTimeLater = 123678
        def root1 = new BuildableTestResultsProvider().tap {
            result("root-1")
            child {
                result("class-1") {
                    startTime(startTimeSooner)
                    endTime(endTimeSooner)
                }
                child {
                    result("test-a") {
                        startTime(startTimeSooner)
                        endTime(startTimeSooner + 10)
                        resultType(TestResult.ResultType.SUCCESS)
                    }
                }
            }
        }
        def root2 = new BuildableTestResultsProvider().tap {
            result("root-2")
            child {
                result("class-1") {
                    startTime(startTimeLater)
                    endTime(endTimeLater)
                }
                child {
                    result("test-a") {
                        startTime(startTimeLater)
                        endTime(startTimeLater + 100)
                        resultType(TestResult.ResultType.FAILURE)
                    }
                }
            }
        }
        def aggregate = new AggregateTestResultsProvider("Aggregate", "Aggregate", [root1, root2])

        when:
        List<PersistentTestResult> results = []
        aggregate.visitChildren {
            results.add(it.result)
            it.visitChildren {
                results.add(it.result)
            }
        }

        then:
        results*.name == ['class-1', 'test-a']
        results*.startTime == [startTimeSooner, startTimeSooner]
        results[0].endTime == endTimeLater
        results[1].endTime == startTimeLater + 100
        // TODO what to do here? classes must be merged, but methods are actually not merged and are reported as separate results?
        1 * provider1.visitClasses(_) >> { Action a -> a.execute(class1) }
        1 * provider2.visitClasses(_) >> { Action a -> a.execute(class2) }
        1 * action.execute(_) >> { TestClassResult r ->
            assert r.id == 1
            assert r.className == 'class-1'
            assert r.startTime == startTimeSooner
            assert r.results.any { TestMethodResult m ->
                m.name == 'methodFoo' && m.resultType == TestResult.ResultType.SUCCESS
            }
            assert r.results.any { TestMethodResult m ->
                m.name == 'methodFoo' && m.resultType == TestResult.ResultType.FAILURE
            }
        }
        0 * action._
    }
}
