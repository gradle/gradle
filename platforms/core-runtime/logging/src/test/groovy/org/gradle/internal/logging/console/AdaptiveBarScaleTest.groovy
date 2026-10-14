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

package org.gradle.internal.logging.console

import spock.lang.Specification

class AdaptiveBarScaleTest extends Specification {

    def "target exponent is #expected for done #done, running #running and queued #queued"() {
        expect:
        close(AdaptiveBarScale.targetExponent(done, running, queued), expected)

        where:
        done | running | queued | expected
        10   | 0       | 5      | 1.0
        10   | 0       | 10     | 1.0
        1    | 0       | 8      | 0.5
        60   | 2       | 6500   | 0.5
        1240 | 8       | 5312   | 0.7673992673992673
        0    | 0       | 5      | 0.7142857142857143
    }

    def "cells for done #done, running #running and queued #queued with exponent #exponent"() {
        when:
        def cells = AdaptiveBarScale.cells(30, done, running, queued, exponent)

        then:
        close(cells.done, expectedDone)
        close(cells.running, expectedRunning)

        where:
        done | running | queued | exponent | expectedDone       | expectedRunning
        10   | 0       | 20     | 1.0      | 10.0               | 0.0
        10   | 10      | 10     | 1.0      | 10.0               | 10.0
        1528 | 8       | 1524   | 1.0      | 14.51900393184797  | 1.0
        60   | 2       | 6500   | 0.5      | 2.542002204247726  | 1.0
        0    | 0       | 0      | 1.0      | 0.0                | 0.0
    }

    def "exponent drops at once to a lower target"() {
        given:
        def scale = new AdaptiveBarScale()

        expect:
        scale.next(60, 2, 6500, 1, 0, 30) == 0.5
    }

    def "exponent rises by at most 0.5 per second"() {
        given:
        def scale = new AdaptiveBarScale()
        scale.next(60, 2, 6500, 1, 0, 30)

        expect:
        close(scale.next(4000, 8, 2554, 0, 100, 30), 0.55)
        close(scale.next(4000, 8, 2554, 0, 200, 30), 0.6)
    }

    def "exponent does not rise further than the done segment allows while the total does not grow"() {
        given:
        def scale = new AdaptiveBarScale()
        def before = scale.next(336, 8, 2716, 1, 0, 30)
        def doneBefore = AdaptiveBarScale.cells(30, 336, 8, 2716, before).done

        when:
        def after = scale.next(340, 8, 2712, 0, 100, 30)

        then:
        after > before
        after < AdaptiveBarScale.targetExponent(340, 8, 2712)
        AdaptiveBarScale.cells(30, 340, 8, 2712, after).done >= doneBefore - 1e-9
    }

    def "exponent rises freely when the total grew"() {
        given:
        def scale = new AdaptiveBarScale()
        scale.next(336, 8, 2716, 1, 0, 30)

        expect:
        close(scale.next(340, 8, 2712, 1, 100, 30), 0.5147783251231527)
    }

    def "exponent goes to the target when the total shrank"() {
        given:
        def scale = new AdaptiveBarScale()

        expect:
        scale.next(5, 0, 95, 1, 0, 30) == 0.5
        close(scale.next(5, 1, 11, -1, 100, 30), 0.9404761904761905)
    }

    private static boolean close(double actual, double expected) {
        Math.abs(actual - expected) < 1e-9
    }
}
