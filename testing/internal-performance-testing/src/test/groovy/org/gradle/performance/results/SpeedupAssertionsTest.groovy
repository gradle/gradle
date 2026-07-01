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

package org.gradle.performance.results

import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import spock.lang.Specification

class SpeedupAssertionsTest extends Specification {

    def "assertSpeedupAtLeast passes when current clearly clears the floor"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [580L] * 10) // ~1.72x speedup, clearly above the 1.55x floor

        when:
        SpeedupAssertions.assertSpeedupAtLeast(baseline, current, 1.55, "test/floor-ok")

        then:
        noExceptionThrown()
    }

    def "assertSpeedupAtLeast fails with full regression message when current is too slow"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [750L] * 10) // 1.33x speedup

        when:
        SpeedupAssertions.assertSpeedupAtLeast(baseline, current, 1.55, "test/regression")

        then:
        AssertionError e = thrown()
        e.message == """[FAIL] current is not 1.55x faster than baseline (with at least 5% margin)
  observed:  1.33x faster (1 s → 750 ms median, n=10)
  location:  test/regression
  → Either fix the regression in 'current', or — only if the slowdown is intentional and accepted — lower the floor at this location. Do NOT delete the assertion."""
    }

    def "assertSpeedupAtLeast fails on inconclusive observation near the floor"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [650L] * 10) // 1.538x — 0.8% under the 1.55x floor, inside the noise band

        when:
        SpeedupAssertions.assertSpeedupAtLeast(baseline, current, 1.55, "test/floor-inconclusive")

        then:
        AssertionError e = thrown()
        e.message.startsWith("[FAIL] current is not 1.55x faster")
    }

    def "assertSpeedupAtLeast rejects unresolvable factors below the noise floor"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [950L] * 10)

        when:
        SpeedupAssertions.assertSpeedupAtLeast(baseline, current, 1.05, "test/unresolvable")

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("minSpeedup")
        e.message.contains("below the minimum resolvable bound")
    }

    def "assertSpeedupAtMost passes when current stays below the ceiling"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [625L] * 10) // 1.6x speedup, below 1.65x ceiling

        when:
        SpeedupAssertions.assertSpeedupAtMost(baseline, current, 1.65, "test/ceiling-ok")

        then:
        noExceptionThrown()
    }

    def "assertSpeedupAtMost fails with full lock-in message when current is too fast"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [500L] * 10) // 2x speedup

        when:
        SpeedupAssertions.assertSpeedupAtMost(baseline, current, 1.65, "test/lockin")

        then:
        AssertionError e = thrown()
        e.message == """[FAIL] current is more than 1.65x faster than baseline (lock-in ceiling exceeded)
  observed:  2.00x faster (1 s → 500 ms median, n=10)
  location:  test/lockin
  → Looks like an improvement landed. Confirm it is real (re-run, inspect a build scan), then raise both the floor and ceiling at this location so the new range reflects the new normal."""
    }

    def "assertSpeedupAtMost tolerates noise just above the ceiling"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [600L] * 10) // 1.667x — 1% over the 1.65x ceiling, inside the noise band

        when:
        SpeedupAssertions.assertSpeedupAtMost(baseline, current, 1.65, "test/ceiling-noise")

        then:
        noExceptionThrown()
    }

    def "assertSpeedupAtMost rejects unresolvable factors below the noise floor"() {
        given:
        def baseline = list("baseline", [1000L] * 10)
        def current = list("current", [950L] * 10)

        when:
        SpeedupAssertions.assertSpeedupAtMost(baseline, current, 1.05, "test/unresolvable")

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("maxSpeedup")
        e.message.contains("below the minimum resolvable bound")
    }

    def "fails clearly when measurements are missing"() {
        given:
        def baseline = new MeasuredOperationList(name: "baseline")
        def current = list("current", [625L] * 10)

        when:
        SpeedupAssertions.assertSpeedupAtLeast(baseline, current, 1.55, "test/missing")

        then:
        AssertionError e = thrown()
        e.message.contains("missing measurements")
        e.message.contains("test/missing")
    }

    private static MeasuredOperationList list(String name, List<Long> millis) {
        def out = new MeasuredOperationList(name: name)
        millis.each { out.add(new MeasuredOperation(totalTime: Duration.millis(it))) }
        return out
    }
}
