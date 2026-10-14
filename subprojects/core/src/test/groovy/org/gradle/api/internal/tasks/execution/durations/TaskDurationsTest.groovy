/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution.durations

import spock.lang.Specification

import java.time.Duration

class TaskDurationsTest extends Specification {

    def "keeps at most the 3 most recent samples of #input"() {
        expect:
        TaskDurations.of(millis(input)).recent == millis(expected)

        where:
        input        | expected
        [1]          | [1]
        [1, 2, 3]    | [1, 2, 3]
        [1, 2, 3, 4] | [2, 3, 4]
    }

    def "withSample appends and drops the oldest"() {
        expect:
        TaskDurations.of(millis([1, 2, 3])).withSample(Duration.ofMillis(4)).recent == millis([2, 3, 4])
        TaskDurations.of(millis([1])).withSample(Duration.ofMillis(4)).recent == millis([1, 4])
    }

    def "average of #input is #average ms"() {
        expect:
        TaskDurations.of(millis(input)).average == Duration.ofMillis(average)

        where:
        input              | average
        [1000]             | 1000
        [1000, 2000]       | 1500
        [1000, 2000, 4000] | 2333
    }

    def "requires at least one sample"() {
        when:
        TaskDurations.of([])

        then:
        thrown(IllegalArgumentException)
    }

    private static List<Duration> millis(List<Integer> values) {
        values.collect { Duration.ofMillis(it) }
    }
}
