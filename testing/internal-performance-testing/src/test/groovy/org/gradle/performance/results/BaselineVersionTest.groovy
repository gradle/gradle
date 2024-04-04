/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.performance.measure.DataSeries
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import spock.lang.Specification

class BaselineVersionTest extends Specification {
    def baseline = new BaselineVersion("7.5")
    def current = new BaselineVersion("7.6")

    def "does consider changes with high confidence"() {
        baseline.results.addAll([millis(100)] * 10)
        current.results.addAll([millis(115)] * 10)
        def confidence = calculateConfidence(baseline, current)
        def minConfidence = confidence - 0.01

        expect:
        baseline.significantlyFasterThan(current.results, minConfidence)
        baseline.significantlyFasterByMedianThan(current.results, 0.14)
        current.significantlySlowerThan(baseline.results, minConfidence)
    }

    def "does not consider changes with less then 10 milliseconds as significant"() {
        baseline.results.addAll([millis(100)] * 10)
        current.results.addAll([millis(105)] * 10)
        def confidence = calculateConfidence(baseline, current)
        def minConfidence = confidence - 0.01

        expect:
        !baseline.significantlyFasterThan(current.results, minConfidence)
        !current.significantlySlowerThan(baseline.results, minConfidence)
    }

    def "always considers changes with more than 20% difference as significant"() {
        baseline.results.addAll([millis(100)] * 10)
        current.results.addAll([millis(121)] * 10)
        def confidence = calculateConfidence(baseline, current)
        def tooHighConfidence = confidence + 0.01

        expect:
        baseline.significantlyFasterThan(current.results, tooHighConfidence)
        current.significantlySlowerThan(baseline.results, tooHighConfidence)
    }

    private double calculateConfidence(BaselineVersion first, BaselineVersion second) {
        DataSeries.confidenceInDifference(first.results.getTotalTime(), second.results.totalTime)
    }

    MeasuredOperation millis(long millis) {
        new MeasuredOperation(totalTime: Duration.millis(millis))
    }
}
