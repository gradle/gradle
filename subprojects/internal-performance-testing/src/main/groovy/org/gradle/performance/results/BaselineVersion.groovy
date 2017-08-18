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

package org.gradle.performance.results

import groovy.transform.CompileStatic
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.Duration

import static PrettyCalculator.toMillis

@CompileStatic
class BaselineVersion implements VersionResults {
    final String version
    final MeasuredOperationList results = new MeasuredOperationList()

    BaselineVersion(String version) {
        this.version = version
        results.name = "Gradle $version"
    }

    String getSpeedStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        def thisVersionMedian = results.totalTime.median
        def currentVersionMedian = current.totalTime.median
        if (currentVersionMedian && thisVersionMedian) {
            if (currentVersionMedian > thisVersionMedian) {
                sb.append "Speed $displayName: we're slower than $version.\n"
            } else {
                sb.append "Speed $displayName: AWESOME! we're faster than $version :D\n"
            }

            def diff = currentVersionMedian - thisVersionMedian
            def desc = diff > Duration.millis(0) ? "slower" : "faster"
            sb.append("Difference: ${diff.abs().format()} $desc (${toMillis(diff.abs())}), ${PrettyCalculator.percentChange(currentVersionMedian, thisVersionMedian)}%, max regression: ${getMaxExecutionTimeRegression(current).format()}\n")
            sb.append(current.speedStats)
            sb.append(results.speedStats)
            sb.append("\n")
            sb.toString()
        } else {
            sb.append("Speed measurement is not available (probably due to a build failure)")
        }
    }

    boolean fasterThan(MeasuredOperationList current) {
        results.totalTime && current.totalTime.median - results.totalTime.median > getMaxExecutionTimeRegression(current)
    }

    /*
      Allows the results to deviate from the baseline by at most 1.5 standard deviations of the mean.
      This seems to be a good trade-off between strictness and avoiding false positives
     */
    Amount<Duration> getMaxExecutionTimeRegression(MeasuredOperationList current) {
        (results.totalTime.standardErrorOfMean + current.totalTime.standardErrorOfMean) / 2 * 1.5
    }
}
